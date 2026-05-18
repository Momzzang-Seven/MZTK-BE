package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAddressMismatchException;
import momzzangseven.mztkbe.global.error.web3.TreasuryPrivateKeyInvalidException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ProvisionTreasuryKeyUseCase;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;

/**
 * MOM-444 / PR #177 R3+R4 provisioning orchestrator.
 *
 * <p>Thin outer shell: validates the command, derives the wallet address from the supplied raw key,
 * fails fast on {@code expectedAddress} mismatch, then hands off to {@link
 * ProvisionTreasuryKeyTransactionalDelegate#lockedCommit} for everything that requires the row lock
 * — including the conditional KMS mint, the DB row write, and the AFTER_COMMIT event publication.
 * The mint chain ({@code createKey / import / signDigest}) used to run unconditionally outside the
 * transaction; after PR #177 R4 it lives inside the delegate and runs only for the actions that
 * actually need a fresh KMS key, so admin retries (C4 / C5) no longer leave orphan keys behind on
 * cleanup failure.
 *
 * <p>The outer try/catch survives only as the audit safety net for failures that escape the
 * delegate without already writing a treasury_provision audit row (e.g. an unexpected
 * RuntimeException between the delegate's catch handlers and the rollback sync). The {@code
 * failureAuditWritten} AtomicBoolean is the interlock the delegate flips when it has already
 * recorded a failure row.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProvisionTreasuryKeyService implements ProvisionTreasuryKeyUseCase {

  // PR #177 R11 — narrow the fresh-INSERT race retry to the only DIV cause that is actually a
  // race. Other DIV causes on this path (treasury_address CHECK from V069's
  // ck_web3_treasury_wallets_kms_key_id_required, the kms_key_id UNIQUE
  // uk_web3_treasury_wallets_kms_key_id from V069) are input/state errors that must surface as
  // their own failure audit, not be silently replayed as ALREADY_PROVISIONED.
  // The wallet_alias UNIQUE index keeps its original V014 name across the V059 table rename, so
  // the value below maps directly to Flyway V014__evolve_web3_treasury_keys.sql:28.
  private static final String WALLET_ALIAS_UNIQUE_CONSTRAINT = "uk_web3_treasury_keys_wallet_alias";

  private final ProvisionTreasuryKeyTransactionalDelegate delegate;
  private final TreasuryAuditRecorder treasuryAuditRecorder;

  @Override
  @AdminOnly(
      actionType = "TREASURY_KEY_PROVISION",
      targetType = AuditTargetType.TREASURY_KEY,
      operatorId = "#command.operatorUserId()",
      targetId = "#result != null ? #result.walletAddress() : null")
  public ProvisionTreasuryKeyResult execute(ProvisionTreasuryKeyCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();

    String derivedAddress = deriveAddress(command.rawPrivateKey());
    if (!derivedAddress.equalsIgnoreCase(command.expectedAddress())) {
      treasuryAuditRecorder.record(command.operatorUserId(), null, false, "ADDRESS_MISMATCH");
      throw new TreasuryWalletAddressMismatchException(
          "derived address does not match expectedAddress");
    }

    AtomicBoolean failureAuditWritten = new AtomicBoolean(false);
    try {
      return delegate.lockedCommit(command, derivedAddress, failureAuditWritten, false);
    } catch (DataIntegrityViolationException raceLoser) {
      // PR #177 R6-C — fresh-INSERT race. The first lockedCommit lost the UNIQUE(alias) INSERT
      // race; its rollback sync already disposed the orphan KMS key. Retry once with the
      // race-retry flag so handleExistingProvisionedRow short-circuits to a deterministic 409
      // ALREADY_PROVISIONED (TREASURY_004) instead of the generic DATA_INTEGRITY_VIOLATION
      // mapping the GlobalExceptionHandler would otherwise apply.
      // PR #177 R11 — restrict the retry to the wallet_alias UNIQUE cause; other DIV causes on
      // this path (treasury_address CHECK, kms_key_id UNIQUE) are not races and must escalate
      // through the generic RuntimeException handler below with their own failure audit.
      if (!isWalletAliasUniqueViolation(raceLoser)) {
        if (failureAuditWritten.compareAndSet(false, true)) {
          treasuryAuditRecorder.record(
              command.operatorUserId(),
              derivedAddress,
              false,
              raceLoser.getClass().getSimpleName());
        }
        throw raceLoser;
      }
      log.warn(
          "Fresh-provision race detected for alias={}, replaying as race-retry",
          command.role().toAlias());
      AtomicBoolean retryFailureAuditWritten = new AtomicBoolean(false);
      try {
        return delegate.lockedCommit(command, derivedAddress, retryFailureAuditWritten, true);
      } catch (RuntimeException retryEx) {
        if (retryFailureAuditWritten.compareAndSet(false, true)) {
          treasuryAuditRecorder.record(
              command.operatorUserId(), derivedAddress, false, retryEx.getClass().getSimpleName());
        }
        throw retryEx;
      }
    } catch (RuntimeException e) {
      if (failureAuditWritten.compareAndSet(false, true)) {
        treasuryAuditRecorder.record(
            command.operatorUserId(), derivedAddress, false, e.getClass().getSimpleName());
      }
      throw e;
    }
  }

  private static boolean isWalletAliasUniqueViolation(DataIntegrityViolationException ex) {
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException constraint
          && WALLET_ALIAS_UNIQUE_CONSTRAINT.equals(constraint.getConstraintName())) {
        return true;
      }
      if (cause.getCause() == cause) {
        break;
      }
    }
    return false;
  }

  private static String deriveAddress(String rawPrivateKey) {
    String normalized = normalizePrivateKey(rawPrivateKey);
    return Credentials.create(normalized).getAddress().toLowerCase(Locale.ROOT);
  }

  private static String normalizePrivateKey(String rawPrivateKey) {
    if (rawPrivateKey == null || rawPrivateKey.isBlank()) {
      throw new TreasuryPrivateKeyInvalidException("rawPrivateKey is required");
    }
    String normalized = rawPrivateKey.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("0x")) {
      normalized = normalized.substring(2);
    }
    if (normalized.length() != 64) {
      throw new TreasuryPrivateKeyInvalidException("rawPrivateKey must be 32-byte hex");
    }
    if (!normalized.matches("^[0-9a-f]{64}$")) {
      throw new TreasuryPrivateKeyInvalidException("rawPrivateKey must be hex string");
    }
    return normalized;
  }
}
