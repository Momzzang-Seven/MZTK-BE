package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAddressMismatchException;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAlreadyProvisionedException;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.error.web3.TreasuryPrivateKeyInvalidException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ProvisionTreasuryKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.RecordTreasuryAuditUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyMaterialWrapperPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SignDigestPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.TreasuryAdvisoryLockPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.AliasProvisionedAuditEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.KeyLifecycleEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.web3j.crypto.Credentials;

/**
 * KMS-backed treasury wallet provisioning with cohort support (MOM-444). A cohort is the set of
 * wallet rows sharing one {@code (treasury_address, kms_key_id)} pair; the first alias for an
 * address creates a fresh KMS key, later aliases <em>co-bind</em> to the cohort's existing key.
 *
 * <p><b>Fresh provision.</b> When no sibling row exists for the derived address, the service owns
 * the {@code CreateKey} → {@code ImportKeyMaterial} → sanity sign → DB save chain inside a single
 * {@link Transactional}. {@code CreateAlias} runs post-commit: once the row commits, a {@link
 * KeyLifecycleEvent.BoundAlias} is published and an AFTER_COMMIT handler invokes {@code
 * BindKmsAliasUseCase}.
 *
 * <p><b>Co-bind.</b> When sibling rows already exist for the address, the new alias reuses their
 * shared {@code kms_key_id} — no new KMS key is created, and {@code registerCleanupOnRollback} is
 * deliberately NOT registered so a rollback of the new row never tears down the shared key
 * (invariant #3). Co-bind is policy-gated: it is rejected unless every sibling is ACTIVE.
 *
 * <p><b>Mixed-cohort guard.</b> The {@code treasury_address <-> kms_key_id} 1:1 invariant is
 * strict-rejected at this endpoint (no reconcile use case): if sibling rows disagree on {@code
 * kms_key_id}, provisioning aborts with {@code COHORT_STATE_INCONSISTENT}. The V073 DB trigger is
 * the backstop for raw-SQL mistakes.
 *
 * <p><b>Advisory lock.</b> After deriving the address, the service acquires a transaction-scoped
 * advisory lock on it so concurrent provision/disable/archive calls on the same address serialize —
 * this is what guarantees "KMS CreateKey once per cohort" even under a provision-provision race.
 *
 * <p><b>Operator retry / alias repair.</b> When an existing row already carries a {@code
 * kms_key_id} but the KMS alias is missing or stale, re-running {@code POST /provision} enters
 * <em>alias-repair mode</em>: the service skips {@code CreateKey}/{@code ImportKeyMaterial} and
 * just re-publishes the events so the handler can rebind the alias idempotently.
 *
 * <p>On any failure inside the transactional body of a <em>fresh provision</em>, the {@link
 * Transactional} rollback together with {@code cleanupKmsKey} (disable + 7-day scheduled deletion)
 * ensures a half-provisioned KMS key cannot accumulate; a {@link TransactionSynchronization}
 * registered right after {@code createKey()} covers the proxy-commit boundary. An {@link
 * AtomicBoolean} interlock guarantees the cleanup body runs at most once.
 *
 * <p>Failure audits are recorded inline via {@link RecordTreasuryAuditUseCase} ({@code
 * REQUIRES_NEW}) so they survive an outer rollback. The success audit is moved to an AFTER_COMMIT
 * handler ({@code TreasuryAuditEventHandler}) driven by {@link AliasProvisionedAuditEvent}.
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
  private final RecordTreasuryAuditUseCase treasuryAuditRecorder;
  private final TreasuryAdvisoryLockPort treasuryAdvisoryLockPort;
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
      treasuryAuditRecorder.record(
          command.operatorUserId(), walletAlias, null, false, "ADDRESS_MISMATCH");
      throw new TreasuryWalletAddressMismatchException(
          "derived address does not match expectedAddress");
    }

    // Serialize lifecycle operations on this address so KMS CreateKey fires once per cohort even
    // under a provision-provision race. Released on transaction completion.
    treasuryAdvisoryLockPort.lockForAddress(derivedAddress);

    Optional<TreasuryWallet> byAlias = loadTreasuryWalletPort.loadByAlias(walletAlias);
    if (byAlias.isPresent() && !derivedAddress.equalsIgnoreCase(byAlias.get().getWalletAddress())) {
      treasuryAuditRecorder.record(
          command.operatorUserId(), walletAlias, derivedAddress, false, "ADDRESS_MISMATCH");
      throw new TreasuryWalletAddressMismatchException(
          "derived address does not match the existing legacy row's walletAddress for alias '"
              + walletAlias
              + "'");
    }

    // Single cohort query reused by the mixed-cohort guard, the alias-repair branch and the
    // co-bind branch. Loaded FOR UPDATE and before alias-repair so a mixed cohort cannot be
    // bypassed. The byAlias row (if any) is excluded — byAlias passed the address-mismatch guard
    // above so it shares derivedAddress and would otherwise appear here.
    List<TreasuryWallet> siblings =
        loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(derivedAddress).stream()
            .filter(s -> byAlias.isEmpty() || !s.getId().equals(byAlias.get().getId()))
            .toList();

    assertCohortConsistent(command, walletAlias, derivedAddress, byAlias.orElse(null), siblings);

    if (byAlias.isPresent() && byAlias.get().getKmsKeyId() != null) {
      return handleExistingProvisionedRow(command, byAlias.get(), derivedAddress, role);
    }

    if (!siblings.isEmpty()) {
      return coBindToCohort(
          command, walletAlias, derivedAddress, role, byAlias.orElse(null), siblings);
    }

    return freshProvision(command, walletAlias, derivedAddress, role, byAlias.orElse(null));
  }

  /**
   * Strict-reject a mixed cohort: the {@code treasury_address <-> kms_key_id} 1:1 invariant must
   * hold before any provisioning branch runs. The V073 trigger is the DB-level backstop; this is
   * the application-level guard so a normal endpoint never silently provisions onto an inconsistent
   * cohort.
   */
  private void assertCohortConsistent(
      ProvisionTreasuryKeyCommand command,
      String walletAlias,
      String derivedAddress,
      TreasuryWallet byAlias,
      List<TreasuryWallet> siblings) {
    long distinctSiblingKeys =
        siblings.stream().map(TreasuryWallet::getKmsKeyId).distinct().count();
    boolean siblingsDisagree = distinctSiblingKeys > 1;
    boolean aliasDisagrees =
        byAlias != null
            && byAlias.getKmsKeyId() != null
            && !siblings.isEmpty()
            && !byAlias.getKmsKeyId().equals(siblings.get(0).getKmsKeyId());
    if (siblingsDisagree || aliasDisagrees) {
      treasuryAuditRecorder.record(
          command.operatorUserId(),
          walletAlias,
          derivedAddress,
          false,
          "COHORT_STATE_INCONSISTENT");
      throw new TreasuryWalletStateException(
          "treasury address '"
              + derivedAddress
              + "' has a mixed cohort (rows disagree on kms_key_id) — resolve via the runbook"
              + " before provisioning");
    }
  }

  /**
   * Co-bind a new (or legacy null-key) alias to an existing cohort's shared KMS key. No new KMS key
   * is created and {@code registerCleanupOnRollback} is intentionally NOT registered — a rollback
   * of this row must never tear down the shared key (invariant #3). Gated on every sibling being
   * ACTIVE so the new ACTIVE row stays consistent with the cohort (invariant #2).
   */
  private ProvisionTreasuryKeyResult coBindToCohort(
      ProvisionTreasuryKeyCommand command,
      String walletAlias,
      String derivedAddress,
      TreasuryRole role,
      TreasuryWallet byAlias,
      List<TreasuryWallet> siblings) {
    boolean allActive =
        siblings.stream().allMatch(s -> s.getStatus() == TreasuryWalletStatus.ACTIVE);
    if (!allActive) {
      treasuryAuditRecorder.record(
          command.operatorUserId(), walletAlias, derivedAddress, false, "COHORT_NOT_ALL_ACTIVE");
      throw new TreasuryWalletAlreadyProvisionedException(
          "treasury address '"
              + derivedAddress
              + "' has a cohort that is not fully ACTIVE — cannot co-bind alias '"
              + walletAlias
              + "'");
    }

    String sharedKmsKeyId = siblings.get(0).getKmsKeyId();
    TreasuryWallet wallet =
        byAlias != null
            ? TreasuryWallet.backfill(byAlias, sharedKmsKeyId, clock)
            : TreasuryWallet.provision(walletAlias, sharedKmsKeyId, derivedAddress, role, clock);
    TreasuryWallet saved = saveTreasuryWalletPort.save(wallet);

    publishAliasProvisioned(command, walletAlias, derivedAddress, true, false);
    publishBoundAlias(command, walletAlias, sharedKmsKeyId, derivedAddress);

    return ProvisionTreasuryKeyResult.from(saved, role);
  }

  /**
   * First alias for an address: create a fresh KMS key, import the operator-supplied material,
   * sanity-sign, persist the row, and register rollback cleanup for the new key.
   */
  private ProvisionTreasuryKeyResult freshProvision(
      ProvisionTreasuryKeyCommand command,
      String walletAlias,
      String derivedAddress,
      TreasuryRole role,
      TreasuryWallet byAlias) {
    String kmsKeyId = null;
    byte[] rawPrivateKey = decodePrivateKey(command.rawPrivateKey());
    byte[] wrappedKey = null;
    AtomicBoolean cleanupInvoked = new AtomicBoolean(false);
    try {
      kmsKeyId = kmsKeyLifecyclePort.createKey();
      registerCleanupOnRollback(
          kmsKeyId, cleanupInvoked, command.operatorUserId(), walletAlias, derivedAddress);
      KmsKeyLifecyclePort.ImportParams params =
          kmsKeyLifecyclePort.getParametersForImport(kmsKeyId);
      wrappedKey = kmsKeyMaterialWrapperPort.wrap(rawPrivateKey, params.wrappingPublicKey());
      kmsKeyLifecyclePort.importKeyMaterial(kmsKeyId, wrappedKey, params.importToken());

      byte[] digest = new byte[SANITY_DIGEST_BYTES];
      secureRandom.nextBytes(digest);
      signDigestPort.signDigest(kmsKeyId, digest, derivedAddress);

      TreasuryWallet wallet =
          byAlias != null
              ? TreasuryWallet.backfill(byAlias, kmsKeyId, clock)
              : TreasuryWallet.provision(walletAlias, kmsKeyId, derivedAddress, role, clock);
      TreasuryWallet saved = saveTreasuryWalletPort.save(wallet);

      publishAliasProvisioned(command, walletAlias, derivedAddress, false, false);
      publishBoundAlias(command, walletAlias, kmsKeyId, derivedAddress);

      return ProvisionTreasuryKeyResult.from(saved, role);
    } catch (RuntimeException e) {
      if (cleanupInvoked.compareAndSet(false, true)) {
        cleanupKmsKey(kmsKeyId);
      }
      treasuryAuditRecorder.record(
          command.operatorUserId(),
          walletAlias,
          derivedAddress,
          false,
          e.getClass().getSimpleName());
      throw e;
    } finally {
      zeroize(rawPrivateKey);
      zeroize(wrappedKey);
    }
  }

  private void publishAliasProvisioned(
      ProvisionTreasuryKeyCommand command,
      String walletAlias,
      String derivedAddress,
      boolean coBind,
      boolean aliasRepairMode) {
    applicationEventPublisher.publishEvent(
        new AliasProvisionedAuditEvent(
            walletAlias, derivedAddress, command.operatorUserId(), coBind, aliasRepairMode));
  }

  private void publishBoundAlias(
      ProvisionTreasuryKeyCommand command,
      String walletAlias,
      String kmsKeyId,
      String derivedAddress) {
    applicationEventPublisher.publishEvent(
        new KeyLifecycleEvent.BoundAlias(
            kmsKeyId, walletAlias, derivedAddress, command.operatorUserId()));
  }

  /**
   * Register a {@link TransactionSynchronization} on the current transaction so that {@code
   * cleanupKmsKey} runs even when the failure happens at the proxy-commit boundary (where the
   * body-level catch cannot fire). The {@code cleanupInvoked} interlock is shared with the catch
   * block, guaranteeing single execution even when both paths fire on an in-method exception.
   *
   * <p>Only registered on the fresh-provision path — the co-bind path must never tear down a shared
   * cohort key on rollback (invariant #3).
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
      String walletAlias,
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
                    operatorUserId, walletAlias, derivedAddress, false, "TX_STATUS_UNKNOWN");
            }
          }
        });
  }

  /**
   * Handle an operator retry where the row already carries a {@code kms_key_id}. If the KMS alias
   * is missing or pointing at a {@code PENDING_DELETION} / {@code DISABLED} key, treat this as
   * <em>alias-repair</em>: re-publish the events so the AFTER_COMMIT handler can rebind the alias
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
          command.operatorUserId(),
          existing.getWalletAlias(),
          derivedAddress,
          false,
          "ALREADY_PROVISIONED");
      throw new TreasuryWalletAlreadyProvisionedException(
          "treasury wallet already provisioned for alias '" + existing.getWalletAlias() + "'");
    }
    log.warn(
        "Re-publishing provisioned events in alias-repair mode (alias={}, aliasState={})",
        existing.getWalletAlias(),
        aliasState);
    publishAliasProvisioned(
        command, existing.getWalletAlias(), existing.getWalletAddress(), false, true);
    publishBoundAlias(
        command, existing.getWalletAlias(), existing.getKmsKeyId(), existing.getWalletAddress());
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
