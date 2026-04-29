package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.error.treasury.KmsAliasAlreadyExistsException;
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
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;

/**
 * KMS-backed treasury wallet provisioning. Replaces the legacy AES-GCM-encrypted private key flow.
 *
 * <p>The whole provisioning happens inside a single {@link Transactional} so the persisted row and
 * the KMS-side resources commit or roll back together. On failure the service best-effort cleans up
 * any KMS resources it allocated (disable + 7-day scheduled deletion) so a half-provisioned key
 * cannot accumulate.
 *
 * <p><b>KMS step ordering</b> — {@code CreateAlias} runs <em>last</em>, after the sanity sign and
 * the JPA save. With this ordering any failure in {@code CreateKey} → {@code ImportKeyMaterial} →
 * sanity → save triggers the {@link Transactional} rollback while the alias has not been created
 * yet, so the only artefact left to clean up is the freshly created KMS key. {@code CreateAlias}
 * itself can still race with a stale alias from a prior failed run (AWS-side success + client-side
 * failure); we recover idempotently by inspecting the existing alias target via {@link
 * KmsKeyLifecyclePort#describeAliasTarget(String)} and re-pointing it to the new key with {@link
 * KmsKeyLifecyclePort#updateAlias(String, String)} when the previous target is {@code
 * PENDING_DELETION} / {@code DISABLED}.
 *
 * <p>Audit entries are recorded via {@link TreasuryAuditRecorder} which runs them in {@code
 * REQUIRES_NEW} so they survive even when the outer transaction rolls back — the operator must
 * always see a row in {@code web3_treasury_provision_audits} regardless of business-flow outcome.
 * Bean-validation / null-command failures short-circuit before audit and are not recorded; the
 * controller's {@code @Valid} chain is the source of truth for those cases. Routing audit writes
 * through a separate bean (rather than a {@code @Transactional} method on this same class) is
 * required because Spring AOP cannot intercept a self-invocation, so an inline {@code recordAudit}
 * would silently lose its {@code REQUIRES_NEW} guarantee.
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
    if (loadTreasuryWalletPort.existsByAliasOrAddress(walletAlias, derivedAddress)) {
      treasuryAuditRecorder.record(
          command.operatorUserId(), derivedAddress, false, "ALREADY_PROVISIONED");
      throw new TreasuryWalletAlreadyProvisionedException(
          "treasury wallet already provisioned for alias '" + walletAlias + "'");
    }

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

      TreasuryWallet wallet =
          TreasuryWallet.provision(walletAlias, kmsKeyId, derivedAddress, role, clock);
      TreasuryWallet saved = saveTreasuryWalletPort.save(wallet);

      bindAliasIdempotent(walletAlias, kmsKeyId);

      treasuryAuditRecorder.record(command.operatorUserId(), derivedAddress, true, null);
      return ProvisionTreasuryKeyResult.from(saved, role);
    } catch (RuntimeException e) {
      cleanupKmsKey(kmsKeyId);
      treasuryAuditRecorder.record(
          command.operatorUserId(), derivedAddress, false, e.getClass().getSimpleName());
      throw e;
    } finally {
      zeroize(rawPrivateKey);
      zeroize(wrappedKey);
    }
  }

  /**
   * Bind {@code walletAlias} to {@code kmsKeyId}. If {@code CreateAlias} reports the alias is
   * already taken, treat a {@code PENDING_DELETION} / {@code DISABLED} target as a recoverable
   * ghost from a prior failed run and re-point the alias at the new key via {@code UpdateAlias};
   * any other target is an out-of-band conflict and surfaces as {@code ALREADY_PROVISIONED}.
   */
  private void bindAliasIdempotent(String walletAlias, String kmsKeyId) {
    try {
      kmsKeyLifecyclePort.createAlias(walletAlias, kmsKeyId);
    } catch (KmsAliasAlreadyExistsException ex) {
      KmsKeyState existingTarget = kmsKeyLifecyclePort.describeAliasTarget(walletAlias);
      if (existingTarget == KmsKeyState.PENDING_DELETION
          || existingTarget == KmsKeyState.DISABLED) {
        log.warn(
            "Recovering ghost alias from a prior failed provision run "
                + "(alias={}, previousState={}); rebinding to kmsKeyId={}",
            walletAlias,
            existingTarget,
            kmsKeyId);
        kmsKeyLifecyclePort.updateAlias(walletAlias, kmsKeyId);
      } else {
        throw new TreasuryWalletAlreadyProvisionedException(
            "alias '"
                + walletAlias
                + "' is already bound to a KMS key in state "
                + existingTarget
                + " (out-of-band provisioning detected)");
      }
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
