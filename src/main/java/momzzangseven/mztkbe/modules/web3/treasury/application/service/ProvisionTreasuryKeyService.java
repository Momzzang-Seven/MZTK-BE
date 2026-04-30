package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAddressMismatchException;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAlreadyProvisionedException;
import momzzangseven.mztkbe.global.error.web3.TreasuryPrivateKeyInvalidException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ProvisionTreasuryKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyMaterialWrapperPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SignDigestPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletProvisionedEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.web3j.crypto.Credentials;

/**
 * KMS-backed treasury wallet provisioning. Replaces the legacy AES-GCM-encrypted private key flow.
 *
 * <p>The service owns the {@code CreateKey} → {@code ImportKeyMaterial} → sanity sign → DB save
 * chain inside a single {@link Transactional}. {@code CreateAlias} no longer runs inside this
 * method — once the row commits, a {@link TreasuryWalletProvisionedEvent} is published and an
 * AFTER_COMMIT handler invokes {@code BindKmsAliasUseCase}. This closes the previous "createAlias
 * succeeded → outer commit failed" race that left an ENABLED key bound to an alias with no DB row,
 * where R-2's ghost-alias recovery could not fire because the body-level catch block does not run
 * when the failure happens at the proxy commit boundary.
 *
 * <p><b>Operator retry / alias repair.</b> When an existing row already carries a {@code
 * kms_key_id} but the KMS alias is missing or stale (the post-commit handler failed or the alias
 * was reaped externally), re-running {@code POST /provision} with the same input enters
 * <em>alias-repair mode</em>: the service skips {@code CreateKey}/{@code ImportKeyMaterial} and
 * just re-publishes the event so the handler can rebind the alias idempotently.
 *
 * <p>On any failure inside the transactional body the {@link Transactional} rollback together with
 * {@code cleanupKmsKey} (disable + 7-day scheduled deletion) ensures a half-provisioned KMS key
 * cannot accumulate. As a safety net for the proxy-commit boundary — where a body-level catch
 * cannot run — a {@link TransactionSynchronization} is registered immediately after {@code
 * createKey()} succeeds. Its {@code afterCompletion} hook only runs cleanup on confirmed {@code
 * STATUS_ROLLED_BACK}; on {@code STATUS_UNKNOWN} (or any unrecognised status) it skips cleanup and
 * records an alert audit row instead, because the DB commit may actually have succeeded and tearing
 * down the KMS key would orphan an ACTIVE wallet row pointing at a disabled/pending-deletion key.
 * An {@link AtomicBoolean} interlock guarantees the cleanup body executes at most once even when
 * both the catch path and the synchronization fire (in-method exception → tx rollback).
 *
 * <p>Failure audits (caught exceptions, address-mismatch, already-provisioned) are recorded inline
 * via {@link TreasuryAuditRecorder} ({@code REQUIRES_NEW}) so they survive an outer rollback. The
 * success audit is moved to an AFTER_COMMIT handler ({@code TreasuryAuditEventHandler}) so it only
 * lands once the wallet row has actually committed; recording it inline let the audit row survive a
 * proxy-boundary commit failure that silently rolled the wallet row back. Bean-validation /
 * null-command failures short-circuit before audit and are not recorded; the controller's
 * {@code @Valid} chain is the source of truth for those cases.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProvisionTreasuryKeyService implements ProvisionTreasuryKeyUseCase {

  private static final int CLEANUP_PENDING_WINDOW_DAYS = 7;
  private static final int RAW_PRIVATE_KEY_BYTES = 32;
  private static final int SANITY_DIGEST_BYTES = 32;

  private final LoadTreasuryWalletPort loadTreasuryWalletPort;
  private final SaveTreasuryWalletPort saveTreasuryWalletPort;
  private final KmsKeyLifecyclePort kmsKeyLifecyclePort;
  private final KmsKeyMaterialWrapperPort kmsKeyMaterialWrapperPort;
  private final SignDigestPort signDigestPort;
  private final TreasuryAuditRecorder treasuryAuditRecorder;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  @Transactional
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

    TreasuryRole role = command.role();
    String walletAlias = role.toAlias();
    String derivedAddress = deriveAddress(command.rawPrivateKey());
    if (!derivedAddress.equalsIgnoreCase(command.expectedAddress())) {
      treasuryAuditRecorder.record(command.operatorUserId(), null, false, "ADDRESS_MISMATCH");
      throw new TreasuryWalletAddressMismatchException(
          "derived address does not match expectedAddress");
    }

    Optional<TreasuryWallet> existing = loadTreasuryWalletPort.loadByAlias(walletAlias);
    if (existing.isPresent()
        && !derivedAddress.equalsIgnoreCase(existing.get().getWalletAddress())) {
      treasuryAuditRecorder.record(
          command.operatorUserId(), derivedAddress, false, "ADDRESS_MISMATCH");
      throw new TreasuryWalletAddressMismatchException(
          "derived address does not match the existing legacy row's walletAddress for alias '"
              + walletAlias
              + "'");
    }
    if (existing.isPresent() && existing.get().getKmsKeyId() != null) {
      return handleExistingProvisionedRow(command, existing.get(), derivedAddress, role);
    }
    if (existing.isEmpty()
        && loadTreasuryWalletPort.existsAddressOwnedByOther(walletAlias, derivedAddress)) {
      treasuryAuditRecorder.record(
          command.operatorUserId(), derivedAddress, false, "ALREADY_PROVISIONED");
      throw new TreasuryWalletAlreadyProvisionedException(
          "wallet address '" + derivedAddress + "' is already bound to a different alias");
    }

    String kmsKeyId = null;
    byte[] rawPrivateKey = decodePrivateKey(command.rawPrivateKey());
    byte[] wrappedKey = null;
    AtomicBoolean cleanupInvoked = new AtomicBoolean(false);
    try {
      kmsKeyId = kmsKeyLifecyclePort.createKey();
      registerCleanupOnRollback(kmsKeyId, cleanupInvoked, command.operatorUserId(), derivedAddress);
      KmsKeyLifecyclePort.ImportParams params =
          kmsKeyLifecyclePort.getParametersForImport(kmsKeyId);
      wrappedKey = kmsKeyMaterialWrapperPort.wrap(rawPrivateKey, params.wrappingPublicKey());
      kmsKeyLifecyclePort.importKeyMaterial(kmsKeyId, wrappedKey, params.importToken());

      byte[] digest = new byte[SANITY_DIGEST_BYTES];
      secureRandom.nextBytes(digest);
      signDigestPort.signDigest(kmsKeyId, digest, derivedAddress);

      TreasuryWallet wallet =
          existing.isPresent()
              ? TreasuryWallet.backfill(existing.get(), kmsKeyId, clock)
              : TreasuryWallet.provision(walletAlias, kmsKeyId, derivedAddress, role, clock);
      TreasuryWallet saved = saveTreasuryWalletPort.save(wallet);

      publishTreasuryWalletProvisionedEvent(command, walletAlias, kmsKeyId, derivedAddress);

      return ProvisionTreasuryKeyResult.from(saved, role);
    } catch (RuntimeException e) {
      if (cleanupInvoked.compareAndSet(false, true)) {
        cleanupKmsKey(kmsKeyId);
      }
      treasuryAuditRecorder.record(
          command.operatorUserId(), derivedAddress, false, e.getClass().getSimpleName());
      throw e;
    } finally {
      zeroize(rawPrivateKey);
      zeroize(wrappedKey);
    }
  }

  private void publishTreasuryWalletProvisionedEvent(
      ProvisionTreasuryKeyCommand command,
      String walletAlias,
      String kmsKeyId,
      String derivedAddress) {
    applicationEventPublisher.publishEvent(
        new TreasuryWalletProvisionedEvent(
            walletAlias, kmsKeyId, derivedAddress, command.operatorUserId(), false));
  }

  /**
   * Register a {@link TransactionSynchronization} on the current transaction so that {@code
   * cleanupKmsKey} runs even when the failure happens at the proxy-commit boundary (where the
   * body-level catch cannot fire). The {@code cleanupInvoked} interlock is shared with the catch
   * block, guaranteeing single execution even when both paths fire on an in-method exception.
   *
   * <p>Only {@code STATUS_ROLLED_BACK} triggers cleanup. {@code STATUS_UNKNOWN} (and any
   * unrecognised future status) is treated conservatively: the DB commit may actually have
   * succeeded, so we skip KMS teardown and record a {@code TX_STATUS_UNKNOWN} alert audit row
   * instead of risking an ACTIVE wallet row pointing at a disabled/pending-deletion key.
   *
   * <p>Guarded by {@link TransactionSynchronizationManager#isSynchronizationActive()} so unit tests
   * that exercise the service without a Spring transaction context (Mockito direct invocation) do
   * not blow up at registration time.
   */
  private void registerCleanupOnRollback(
      String createdKmsKeyId,
      AtomicBoolean cleanupInvoked,
      Long operatorUserId,
      String derivedAddress) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            switch (status) {
              case STATUS_COMMITTED:
                return;
              case STATUS_ROLLED_BACK:
                if (cleanupInvoked.compareAndSet(false, true)) {
                  log.warn(
                      "Outer transaction rolled back; cleaning up KMS key={}", createdKmsKeyId);
                  cleanupKmsKey(createdKmsKeyId);
                }
                return;
              case STATUS_UNKNOWN:
              default:
                log.error(
                    "Outer transaction status={} is not COMMITTED/ROLLED_BACK; skipping KMS"
                        + " cleanup to avoid orphaning a possibly-committed wallet row."
                        + " Operator must verify kmsKeyId={} state.",
                    status,
                    createdKmsKeyId);
                treasuryAuditRecorder.record(
                    operatorUserId, derivedAddress, false, "TX_STATUS_UNKNOWN");
            }
          }
        });
  }

  /**
   * Handle an operator retry where the row already carries a {@code kms_key_id}. If the KMS alias
   * is missing or pointing at a {@code PENDING_DELETION} / {@code DISABLED} key, treat this as
   * <em>alias-repair</em>: re-publish the event so the AFTER_COMMIT handler can rebind the alias
   * idempotently, without re-running {@code CreateKey} / {@code ImportKeyMaterial}. Otherwise this
   * is a true duplicate provisioning attempt.
   */
  private ProvisionTreasuryKeyResult handleExistingProvisionedRow(
      ProvisionTreasuryKeyCommand command,
      TreasuryWallet existing,
      String derivedAddress,
      TreasuryRole role) {
    KmsKeyState aliasState = kmsKeyLifecyclePort.describeAliasTarget(existing.getWalletAlias());
    boolean needsAliasRepair =
        aliasState == KmsKeyState.UNAVAILABLE
            || aliasState == KmsKeyState.PENDING_DELETION
            || aliasState == KmsKeyState.DISABLED;
    if (!needsAliasRepair) {
      treasuryAuditRecorder.record(
          command.operatorUserId(), derivedAddress, false, "ALREADY_PROVISIONED");
      throw new TreasuryWalletAlreadyProvisionedException(
          "treasury wallet already provisioned for alias '" + existing.getWalletAlias() + "'");
    }
    log.warn(
        "Re-publishing provisioned event in alias-repair mode (alias={}, aliasState={})",
        existing.getWalletAlias(),
        aliasState);
    applicationEventPublisher.publishEvent(
        new TreasuryWalletProvisionedEvent(
            existing.getWalletAlias(),
            existing.getKmsKeyId(),
            existing.getWalletAddress(),
            command.operatorUserId(),
            true));
    return ProvisionTreasuryKeyResult.from(existing, role);
  }

  private static String deriveAddress(String rawPrivateKey) {
    String normalized = normalizePrivateKey(rawPrivateKey);
    return Credentials.create(normalized).getAddress().toLowerCase(Locale.ROOT);
  }

  private static byte[] decodePrivateKey(String rawPrivateKey) {
    String normalized = normalizePrivateKey(rawPrivateKey);
    BigInteger value = new BigInteger(normalized, 16);
    byte[] padded = new byte[RAW_PRIVATE_KEY_BYTES];
    byte[] valueBytes = value.toByteArray();
    int srcOffset =
        valueBytes.length > RAW_PRIVATE_KEY_BYTES ? valueBytes.length - RAW_PRIVATE_KEY_BYTES : 0;
    int dstOffset = RAW_PRIVATE_KEY_BYTES - (valueBytes.length - srcOffset);
    System.arraycopy(valueBytes, srcOffset, padded, dstOffset, valueBytes.length - srcOffset);
    return padded;
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

  private void cleanupKmsKey(String kmsKeyId) {
    if (kmsKeyId == null) {
      return;
    }
    try {
      kmsKeyLifecyclePort.disableKey(kmsKeyId);
    } catch (RuntimeException ex) {
      log.warn("Cleanup disableKey failed for kmsKeyId={}", kmsKeyId, ex);
    }
    try {
      kmsKeyLifecyclePort.scheduleKeyDeletion(kmsKeyId, CLEANUP_PENDING_WINDOW_DAYS);
    } catch (RuntimeException ex) {
      log.warn("Cleanup scheduleKeyDeletion failed for kmsKeyId={}", kmsKeyId, ex);
    }
  }

  private static void zeroize(byte[] sensitive) {
    if (sensitive != null) {
      java.util.Arrays.fill(sensitive, (byte) 0);
    }
  }
}
