package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAddressMismatchException;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAlreadyProvisionedException;
import momzzangseven.mztkbe.global.error.web3.TreasuryPrivateKeyInvalidException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ProvisionTreasuryKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyMaterialWrapperPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SignDigestPort;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;

/**
 * MOM-444 3-phase provisioning orchestrator.
 *
 * <p>This service hosts the outer flow only — there is intentionally NO {@code @Transactional} on
 * {@link #execute}. The flow is:
 *
 * <ol>
 *   <li><b>Phase 1 (no TX, no lock):</b> validate, derive address, then pre-mint a KMS key ({@code
 *       createKey} → {@code getParametersForImport} → wrap → {@code importKeyMaterial} → {@code
 *       signDigest} sanity check). Any failure cleans up the partially-minted key.
 *   <li><b>Phase 2 (TX with row lock):</b> delegate to {@link
 *       ProvisionTreasuryKeyTransactionalDelegate#lockedCommit} which acquires {@code
 *       loadByAliasForUpdate} (PESSIMISTIC_WRITE), dispatches to one of five actions
 *       (FreshProvision / Backfill / IdempotentRetry / ReEnableSameKey / ReplaceKey), persists the
 *       row, and publishes the AFTER_COMMIT event.
 *   <li><b>Phase 3 (AFTER_COMMIT handlers, no TX, no lock):</b> KMS alias bind/update, old-key
 *       dispose, or enableKey — each handler is in {@code modules/web3/treasury/infrastructure/
 *       event/} and records {@code KMS_*} audit rows on success/failure.
 * </ol>
 *
 * <p><b>pre-minted key cleanup state machine.</b> The {@code attachedFlag} / {@code cleanupInvoked}
 * interlock ensures the pre-minted key is cleaned up exactly once when it isn't attached to a
 * committed row, and is never disposed when the row commit succeeded with the key attached. See
 * spec §4.0.3.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProvisionTreasuryKeyService implements ProvisionTreasuryKeyUseCase {

  private static final int RAW_PRIVATE_KEY_BYTES = 32;
  private static final int SANITY_DIGEST_BYTES = 32;

  private final ProvisionTreasuryKeyTransactionalDelegate delegate;
  private final KmsKeyLifecyclePort kmsKeyLifecyclePort;
  private final KmsKeyMaterialWrapperPort kmsKeyMaterialWrapperPort;
  private final SignDigestPort signDigestPort;
  private final TreasuryAuditRecorder treasuryAuditRecorder;
  private final SecureRandom secureRandom = new SecureRandom();

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

    String preMintedKeyId = mintAndSanityCheck(command, derivedAddress);
    AtomicBoolean attachedFlag = new AtomicBoolean(false);
    AtomicBoolean cleanupInvoked = new AtomicBoolean(false);

    try {
      return delegate.lockedCommit(
          command, derivedAddress, preMintedKeyId, attachedFlag, cleanupInvoked);
    } catch (TreasuryWalletAlreadyProvisionedException alreadyProvisioned) {
      // delegate already wrote a failure audit row with the legacy "ALREADY_PROVISIONED" reason.
      // Rethrow without re-auditing to keep a single audit row per failed call and to preserve the
      // human-readable failure_reason that operators rely on for filtering.
      throw alreadyProvisioned;
    } catch (RuntimeException e) {
      treasuryAuditRecorder.record(
          command.operatorUserId(), derivedAddress, false, e.getClass().getSimpleName());
      throw e;
    } finally {
      if (!attachedFlag.get() && cleanupInvoked.compareAndSet(false, true)) {
        delegate.cleanupKmsKey(preMintedKeyId);
      }
    }
  }

  /**
   * Phase 1 — pre-mint KMS key + import key material + sanity-sign roundtrip. Runs OUTSIDE any
   * {@code @Transactional}: holding a row lock during the (~1–2s) KMS calls would serialize
   * concurrent provisioning unnecessarily. Failures here cleanup the partially-minted key.
   */
  private String mintAndSanityCheck(ProvisionTreasuryKeyCommand command, String derivedAddress) {
    String kmsKeyId = null;
    byte[] rawPrivateKey = decodePrivateKey(command.rawPrivateKey());
    byte[] wrappedKey = null;
    try {
      kmsKeyId = kmsKeyLifecyclePort.createKey();
      KmsKeyLifecyclePort.ImportParams params =
          kmsKeyLifecyclePort.getParametersForImport(kmsKeyId);
      wrappedKey = kmsKeyMaterialWrapperPort.wrap(rawPrivateKey, params.wrappingPublicKey());
      kmsKeyLifecyclePort.importKeyMaterial(kmsKeyId, wrappedKey, params.importToken());
      byte[] digest = new byte[SANITY_DIGEST_BYTES];
      secureRandom.nextBytes(digest);
      signDigestPort.signDigest(kmsKeyId, digest, derivedAddress);
      return kmsKeyId;
    } catch (RuntimeException e) {
      if (kmsKeyId != null) {
        delegate.cleanupKmsKey(kmsKeyId);
      }
      treasuryAuditRecorder.record(
          command.operatorUserId(), derivedAddress, false, e.getClass().getSimpleName());
      throw e;
    } finally {
      zeroize(rawPrivateKey);
      zeroize(wrappedKey);
    }
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

  private static void zeroize(byte[] sensitive) {
    if (sensitive != null) {
      java.util.Arrays.fill(sensitive, (byte) 0);
    }
  }
}
