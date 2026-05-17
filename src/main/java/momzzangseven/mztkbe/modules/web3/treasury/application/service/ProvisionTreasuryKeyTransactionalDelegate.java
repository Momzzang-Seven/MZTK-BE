package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAlreadyProvisionedException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.AliasTargetInfo;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.DescribeKmsKeyPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyMaterialWrapperPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SignDigestPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletKeyReplacedEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletProvisionedEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletReactivatedEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Transactional inner stage of the MOM-444 / PR #177 R3+R4 provisioning flow.
 *
 * <p>Extracted from {@link ProvisionTreasuryKeyService} into its own bean so Spring AOP can apply
 * {@link Transactional} to {@link #lockedCommit} when the outer service calls it — self-invocation
 * would bypass the proxy and run un-transactional. Holds the DB critical section: acquires a {@code
 * PESSIMISTIC_WRITE} row lock on {@code wallet_alias}, decides which of the five MOM-444 actions
 * applies, and — only for the three actions that need a fresh KMS key (FreshProvision / Backfill /
 * ReplaceKey) — mints the key under the lock. The other two actions (IdempotentRetry,
 * ReEnableSameKey) skip the mint entirely so that the common admin retry path does not leave orphan
 * keys behind even when the cleanup sync fails.
 *
 * <p>KMS side-effects ({@code createAlias / updateAlias / disableKey / scheduleKeyDeletion /
 * enableKey}) still run from AFTER_COMMIT handlers, never from inside this method — keeping the
 * DB-first / KMS-post-commit ordering from the {@code treasury_save_first_ordering} memory intact.
 * The mint chain ({@code createKey / getParametersForImport / wrap / importKeyMaterial /
 * signDigest}) plus the read-only {@code describeAlias} on the IdempotentRetry path are the only
 * KMS calls that happen inside the transaction.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProvisionTreasuryKeyTransactionalDelegate {

  private static final int CLEANUP_PENDING_WINDOW_DAYS = 7;
  private static final int SANITY_DIGEST_BYTES = 32;
  private static final int RAW_PRIVATE_KEY_BYTES = 32;

  private final LoadTreasuryWalletPort loadTreasuryWalletPort;
  private final SaveTreasuryWalletPort saveTreasuryWalletPort;
  private final KmsKeyLifecyclePort kmsKeyLifecyclePort;
  private final KmsKeyMaterialWrapperPort kmsKeyMaterialWrapperPort;
  private final SignDigestPort signDigestPort;
  private final DescribeKmsKeyPort describeKmsKeyPort;
  private final TreasuryAuditRecorder treasuryAuditRecorder;
  private final KmsAuditRecorder kmsAuditRecorder;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * Acquire the alias row lock, decide which action applies, mint a fresh KMS key only when the
   * chosen action requires one, persist the row, and publish the AFTER_COMMIT event.
   *
   * <p>{@code failureAuditWritten} is the outer-catch dedup interlock: whenever this method (or its
   * inner dispatch targets, or the registered rollback synchronizer) records a treasury_provision
   * audit failure row, it must set the flag so the outer service catch does not record a second row
   * with the bare exception class name.
   *
   * <p>{@code isRaceRetry} is the PR #177 R6-C marker: when true, the caller has already
   * encountered a {@link org.springframework.dao.DataIntegrityViolationException} from a prior
   * {@code freshProvision} INSERT racing with another thread that just committed the same alias.
   * The second invocation enters {@link #handleExistingProvisionedRow} on the winner row and is
   * short-circuited to {@link TreasuryWalletAlreadyProvisionedException} so the response is a
   * deterministic 409 instead of leaking the generic {@code DataIntegrityViolationException}.
   */
  @Transactional
  public ProvisionTreasuryKeyResult lockedCommit(
      ProvisionTreasuryKeyCommand command,
      String derivedAddress,
      AtomicBoolean failureAuditWritten,
      boolean isRaceRetry) {

    TreasuryRole role = command.role();
    String walletAlias = role.toAlias();
    Optional<TreasuryWallet> existingOpt = loadTreasuryWalletPort.loadByAliasForUpdate(walletAlias);

    if (existingOpt.isEmpty()) {
      return freshProvision(command, walletAlias, role, derivedAddress, failureAuditWritten);
    }

    TreasuryWallet existing = existingOpt.get();
    boolean addressMatches = derivedAddress.equalsIgnoreCase(existing.getWalletAddress());

    if (existing.getKmsKeyId() == null) {
      return backfill(command, role, existing, derivedAddress, failureAuditWritten);
    }

    if (addressMatches) {
      switch (existing.getStatus()) {
        case ACTIVE:
          return handleExistingProvisionedRow(
              command, existing, derivedAddress, role, failureAuditWritten, isRaceRetry);
        case DISABLED:
          return handleExistingDisabledRow(
              command, existing, derivedAddress, role, failureAuditWritten);
        case ARCHIVED:
          return replaceKey(command, role, existing, derivedAddress, false, failureAuditWritten);
        default:
          throw new IllegalStateException("Unsupported status: " + existing.getStatus());
      }
    }

    boolean disposeOldKey = existing.getStatus() != TreasuryWalletStatus.ARCHIVED;
    return replaceKey(command, role, existing, derivedAddress, disposeOldKey, failureAuditWritten);
  }

  private ProvisionTreasuryKeyResult freshProvision(
      ProvisionTreasuryKeyCommand command,
      String walletAlias,
      TreasuryRole role,
      String derivedAddress,
      AtomicBoolean failureAuditWritten) {
    String kmsKeyId = mintNewKey(command, derivedAddress, failureAuditWritten);
    TreasuryWallet wallet =
        TreasuryWallet.provision(walletAlias, kmsKeyId, derivedAddress, role, clock);
    TreasuryWallet saved = saveTreasuryWalletPort.save(wallet);
    applicationEventPublisher.publishEvent(
        new TreasuryWalletProvisionedEvent(
            walletAlias, kmsKeyId, derivedAddress, command.operatorUserId(), false));
    return ProvisionTreasuryKeyResult.from(saved, role);
  }

  private ProvisionTreasuryKeyResult backfill(
      ProvisionTreasuryKeyCommand command,
      TreasuryRole role,
      TreasuryWallet existing,
      String derivedAddress,
      AtomicBoolean failureAuditWritten) {
    String kmsKeyId = mintNewKey(command, derivedAddress, failureAuditWritten);
    TreasuryWallet wallet = TreasuryWallet.backfill(existing, kmsKeyId, derivedAddress, clock);
    TreasuryWallet saved = saveTreasuryWalletPort.save(wallet);
    applicationEventPublisher.publishEvent(
        new TreasuryWalletProvisionedEvent(
            existing.getWalletAlias(), kmsKeyId, derivedAddress, command.operatorUserId(), false));
    return ProvisionTreasuryKeyResult.from(saved, role);
  }

  private ProvisionTreasuryKeyResult reEnableSameKey(
      ProvisionTreasuryKeyCommand command, TreasuryWallet existing, TreasuryRole role) {
    TreasuryWallet reactivated = TreasuryWallet.reEnable(existing, clock);
    TreasuryWallet saved = saveTreasuryWalletPort.save(reactivated);
    applicationEventPublisher.publishEvent(
        new TreasuryWalletReactivatedEvent(
            existing.getWalletAlias(),
            existing.getKmsKeyId(),
            existing.getWalletAddress(),
            command.operatorUserId()));
    return ProvisionTreasuryKeyResult.from(saved, role);
  }

  private ProvisionTreasuryKeyResult replaceKey(
      ProvisionTreasuryKeyCommand command,
      TreasuryRole role,
      TreasuryWallet existing,
      String derivedAddress,
      boolean disposeOldKey,
      AtomicBoolean failureAuditWritten) {
    String kmsKeyId = mintNewKey(command, derivedAddress, failureAuditWritten);
    TreasuryWallet rotated = TreasuryWallet.replaceKey(existing, kmsKeyId, derivedAddress, clock);
    TreasuryWallet saved = saveTreasuryWalletPort.save(rotated);
    applicationEventPublisher.publishEvent(
        new TreasuryWalletKeyReplacedEvent(
            existing.getWalletAlias(),
            existing.getKmsKeyId(),
            kmsKeyId,
            derivedAddress,
            command.operatorUserId(),
            disposeOldKey));
    return ProvisionTreasuryKeyResult.from(saved, role);
  }

  /**
   * C4 (IdempotentRetry) dispatch. Calls {@code describeAlias} read-only under the lock so the
   * decision between {@code ALREADY_PROVISIONED}, R5 enableKey-failure replay, concern-2 sync-drift
   * recovery, and alias-repair is race-free.
   *
   * <p>{@code targetIdMatches} is computed once with a null-safe equals on {@code
   * aliasInfo.targetKmsKeyId()}. Branches are evaluated in strict order:
   *
   * <ul>
   *   <li>{@code aliasState=ENABLED && targetIdMatches}: wallet is genuinely already provisioned
   *       and consistent with AWS — reject with {@code ALREADY_PROVISIONED}.
   *   <li>{@code aliasState=DISABLED && targetIdMatches} (PR #177 R5): C5 (DISABLED→ACTIVE
   *       re-enable) committed but AFTER_COMMIT {@code enableKey} failed — DB is ACTIVE while the
   *       KMS key is DISABLED. Re-publish {@code TreasuryWalletReactivatedEvent} so the handler
   *       retries {@code enableKey} under the CAS gate. No mint.
   *   <li>{@code aliasState=PENDING_DELETION && targetIdMatches} (PR #177 concern-2): DB/KMS sync
   *       drift — issuing {@code updateAlias} would hit {@code KMSInvalidStateException}. Delegate
   *       to {@link #replaceKey} with {@code disposeOldKey=false} so the dying key is left alone.
   *   <li>else (alias-drift / ghost-alias): re-publish the provisioned event in alias-repair mode
   *       so the AFTER_COMMIT handler restores the alias to the row's kmsKeyId via {@code
   *       updateAlias}. (PR #177 R3.)
   * </ul>
   */
  private ProvisionTreasuryKeyResult handleExistingProvisionedRow(
      ProvisionTreasuryKeyCommand command,
      TreasuryWallet existing,
      String derivedAddress,
      TreasuryRole role,
      AtomicBoolean failureAuditWritten,
      boolean isRaceRetry) {

    // PR #177 R6-C — fresh-INSERT race fast path. The first lockedCommit already lost the
    // UNIQUE(alias) INSERT race against a concurrent winner; the orphan KMS key it minted has
    // been disposed by the rollback sync. This retry only exists to return a deterministic 409
    // instead of the generic DataIntegrityViolationException → DATA_INTEGRITY_VIOLATION mapping.
    // Skip describeAlias entirely so the response does not depend on AFTER_COMMIT alias-bind
    // timing.
    if (isRaceRetry) {
      treasuryAuditRecorder.record(
          command.operatorUserId(), derivedAddress, false, "FRESH_PROVISION_RACE");
      failureAuditWritten.set(true);
      throw new TreasuryWalletAlreadyProvisionedException(
          "treasury wallet fresh-INSERT race lost for alias '" + existing.getWalletAlias() + "'");
    }

    AliasTargetInfo aliasInfo = kmsKeyLifecyclePort.describeAlias(existing.getWalletAlias());
    KmsKeyState aliasState = aliasInfo.state();
    boolean targetIdMatches =
        aliasInfo.targetKmsKeyId() != null
            && existing.getKmsKeyId().equals(aliasInfo.targetKmsKeyId());

    if (aliasState == KmsKeyState.ENABLED && targetIdMatches) {
      treasuryAuditRecorder.record(
          command.operatorUserId(), derivedAddress, false, "ALREADY_PROVISIONED");
      failureAuditWritten.set(true);
      throw new TreasuryWalletAlreadyProvisionedException(
          "treasury wallet already provisioned for alias '" + existing.getWalletAlias() + "'");
    }

    if (aliasState == KmsKeyState.DISABLED && targetIdMatches) {
      log.warn(
          "Re-publishing reactivated event to recover from prior enableKey failure"
              + " (alias={}, kmsKeyId={})",
          existing.getWalletAlias(),
          existing.getKmsKeyId());
      applicationEventPublisher.publishEvent(
          new TreasuryWalletReactivatedEvent(
              existing.getWalletAlias(),
              existing.getKmsKeyId(),
              existing.getWalletAddress(),
              command.operatorUserId()));
      return ProvisionTreasuryKeyResult.from(existing, role);
    }

    if (aliasState == KmsKeyState.PENDING_DELETION && targetIdMatches) {
      log.warn(
          "Recovering DB/KMS sync drift via fresh key (alias={}, pendingDeletionKmsKeyId={})",
          existing.getWalletAlias(),
          existing.getKmsKeyId());
      return replaceKey(command, role, existing, derivedAddress, false, failureAuditWritten);
    }

    log.warn(
        "Re-publishing provisioned event in alias-repair mode (alias={}, aliasState={},"
            + " aliasTarget={}, rowKmsKeyId={})",
        existing.getWalletAlias(),
        aliasState,
        aliasInfo.targetKmsKeyId(),
        existing.getKmsKeyId());
    applicationEventPublisher.publishEvent(
        new TreasuryWalletProvisionedEvent(
            existing.getWalletAlias(),
            existing.getKmsKeyId(),
            existing.getWalletAddress(),
            command.operatorUserId(),
            true));
    return ProvisionTreasuryKeyResult.from(existing, role);
  }

  /**
   * C5 (ReEnableSameKey) dispatch (PR #177 R8). Calls {@code describeAlias} (+ optionally {@code
   * describe} on the row's kmsKeyId) read-only under the lock so the decision between normal
   * re-enable, {@code PENDING_DELETION} recovery, and alias-repair is race-free. Symmetric to
   * {@link #handleExistingProvisionedRow}.
   *
   * <p>Branches by the row's {@link KmsKeyState} first (so a dying key is never reEnabled — that
   * would hit {@code KMSInvalidStateException} in the AFTER_COMMIT enableKey), then by alias {@code
   * targetIdMatches}:
   *
   * <ul>
   *   <li>{@code rowKeyState ∈ {PENDING_DELETION, PENDING_IMPORT, UNAVAILABLE}}: delegate to {@link
   *       #replaceKey} with {@code disposeOldKey=false} so the dying key is left alone. Mirrors the
   *       C4 {@code PENDING_DELETION+match} branch.
   *   <li>{@code rowKeyState ∈ {ENABLED, DISABLED} && targetIdMatches}: standard C5 — alias points
   *       at the row's kmsKeyId; flip DB to ACTIVE and let the AFTER_COMMIT {@code enableKey}
   *       idempotently bring KMS to ENABLED.
   *   <li>{@code rowKeyState ∈ {ENABLED, DISABLED} && !targetIdMatches}: alias-drift / ghost-alias
   *       — row → ACTIVE, publish BOTH {@link TreasuryWalletReactivatedEvent} (enableKey) AND
   *       {@link TreasuryWalletProvisionedEvent} with {@code aliasRepairMode=true} (alias
   *       bind/update). The two AFTER_COMMIT handlers run independently and converge.
   * </ul>
   *
   * <p>The extra {@link DescribeKmsKeyPort#describeFresh(String)} RPC is issued only on the
   * mismatch path; when {@code targetIdMatches=true}, {@code aliasInfo.state()} is already the row
   * key's state. The fresh (uncached) channel is used here so provisioning recovery observes
   * post-mutation KMS state instead of the signing-path's 60s burst-absorber cache.
   */
  private ProvisionTreasuryKeyResult handleExistingDisabledRow(
      ProvisionTreasuryKeyCommand command,
      TreasuryWallet existing,
      String derivedAddress,
      TreasuryRole role,
      AtomicBoolean failureAuditWritten) {

    AliasTargetInfo aliasInfo = kmsKeyLifecyclePort.describeAlias(existing.getWalletAlias());
    boolean targetIdMatches =
        aliasInfo.targetKmsKeyId() != null
            && existing.getKmsKeyId().equals(aliasInfo.targetKmsKeyId());

    KmsKeyState rowKeyState =
        targetIdMatches
            ? aliasInfo.state()
            : describeKmsKeyPort.describeFresh(existing.getKmsKeyId());

    boolean rowKeyDying =
        rowKeyState == KmsKeyState.PENDING_DELETION
            || rowKeyState == KmsKeyState.PENDING_IMPORT
            || rowKeyState == KmsKeyState.UNAVAILABLE;

    if (rowKeyDying) {
      log.warn(
          "Recovering DB/KMS sync drift via fresh key on DISABLED row (alias={},"
              + " rowKmsKeyId={}, rowKeyState={}, aliasState={}, aliasTarget={})",
          existing.getWalletAlias(),
          existing.getKmsKeyId(),
          rowKeyState,
          aliasInfo.state(),
          aliasInfo.targetKmsKeyId());
      return replaceKey(command, role, existing, derivedAddress, false, failureAuditWritten);
    }

    if (targetIdMatches) {
      return reEnableSameKey(command, existing, role);
    }

    log.warn(
        "Re-enabling DISABLED row with alias-repair (alias={}, rowKmsKeyId={}, rowKeyState={},"
            + " aliasState={}, aliasTarget={})",
        existing.getWalletAlias(),
        existing.getKmsKeyId(),
        rowKeyState,
        aliasInfo.state(),
        aliasInfo.targetKmsKeyId());
    TreasuryWallet reactivated = TreasuryWallet.reEnable(existing, clock);
    TreasuryWallet saved = saveTreasuryWalletPort.save(reactivated);
    applicationEventPublisher.publishEvent(
        new TreasuryWalletReactivatedEvent(
            existing.getWalletAlias(),
            existing.getKmsKeyId(),
            existing.getWalletAddress(),
            command.operatorUserId()));
    applicationEventPublisher.publishEvent(
        new TreasuryWalletProvisionedEvent(
            existing.getWalletAlias(),
            existing.getKmsKeyId(),
            existing.getWalletAddress(),
            command.operatorUserId(),
            true));
    return ProvisionTreasuryKeyResult.from(saved, role);
  }

  /**
   * Mint a fresh KMS key plus key material under the row lock: {@code createKey →
   * getParametersForImport → wrap → importKeyMaterial → signDigest}. Registers the rollback cleanup
   * sync as soon as the key id is known so a subsequent save/event failure rolls back the row and
   * disposes the orphan key. On any RuntimeException during mint itself cleans up directly, records
   * the failure audit row, and rethrows.
   */
  private String mintNewKey(
      ProvisionTreasuryKeyCommand command,
      String derivedAddress,
      AtomicBoolean failureAuditWritten) {
    String kmsKeyId = null;
    byte[] rawPrivateKey = decodePrivateKey(command.rawPrivateKey());
    byte[] wrappedKey = null;
    AtomicBoolean cleanupInvoked = new AtomicBoolean(false);
    String walletAlias = command.role().toAlias();
    try {
      kmsKeyId = kmsKeyLifecyclePort.createKey();
      registerCleanupOnRollback(
          kmsKeyId,
          cleanupInvoked,
          failureAuditWritten,
          command.operatorUserId(),
          walletAlias,
          derivedAddress);
      KmsKeyLifecyclePort.ImportParams params =
          kmsKeyLifecyclePort.getParametersForImport(kmsKeyId);
      wrappedKey = kmsKeyMaterialWrapperPort.wrap(rawPrivateKey, params.wrappingPublicKey());
      kmsKeyLifecyclePort.importKeyMaterial(kmsKeyId, wrappedKey, params.importToken());
      byte[] digest = new byte[SANITY_DIGEST_BYTES];
      secureRandom.nextBytes(digest);
      signDigestPort.signDigest(kmsKeyId, digest, derivedAddress);
      return kmsKeyId;
    } catch (RuntimeException e) {
      if (kmsKeyId != null && cleanupInvoked.compareAndSet(false, true)) {
        log.warn("Mint failed mid-flight; cleaning up KMS key={}", kmsKeyId);
        cleanupKmsKey(kmsKeyId, command.operatorUserId(), walletAlias, derivedAddress);
      }
      if (failureAuditWritten.compareAndSet(false, true)) {
        treasuryAuditRecorder.record(
            command.operatorUserId(), derivedAddress, false, e.getClass().getSimpleName());
      }
      throw e;
    } finally {
      zeroize(rawPrivateKey);
      zeroize(wrappedKey);
    }
  }

  private void registerCleanupOnRollback(
      String createdKmsKeyId,
      AtomicBoolean cleanupInvoked,
      AtomicBoolean failureAuditWritten,
      Long operatorUserId,
      String walletAlias,
      String derivedAddress) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        buildCleanupSync(
            createdKmsKeyId,
            cleanupInvoked,
            failureAuditWritten,
            operatorUserId,
            walletAlias,
            derivedAddress));
  }

  /**
   * Factory for the rollback-cleanup synchronization. Package-private so unit tests can drive
   * {@code afterCompletion(...)} directly without spinning up a real transaction context.
   */
  TransactionSynchronization buildCleanupSync(
      String createdKmsKeyId,
      AtomicBoolean cleanupInvoked,
      AtomicBoolean failureAuditWritten,
      Long operatorUserId,
      String walletAlias,
      String derivedAddress) {
    return new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        switch (status) {
          case STATUS_COMMITTED:
            return;
          case STATUS_ROLLED_BACK:
            if (cleanupInvoked.compareAndSet(false, true)) {
              log.warn("Outer transaction rolled back; cleaning up KMS key={}", createdKmsKeyId);
              cleanupKmsKey(createdKmsKeyId, operatorUserId, walletAlias, derivedAddress);
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
            cleanupInvoked.set(true);
            treasuryAuditRecorder.record(
                operatorUserId, derivedAddress, false, "TX_STATUS_UNKNOWN");
            failureAuditWritten.set(true);
        }
      }
    };
  }

  /**
   * Dispose an orphan KMS key after rollback or mid-mint failure. Each step is best-effort and
   * isolated by its own try/catch so a failed {@code disableKey} cannot block the subsequent {@code
   * scheduleKeyDeletion}. Failure paths additionally write a {@code KMS_DISABLE} / {@code
   * KMS_SCHEDULE_DELETION} failure row to {@code web3_treasury_kms_audits} with the {@code
   * kmsKeyId}, so operators can locate the orphan key when AWS-side cleanup did not succeed.
   */
  void cleanupKmsKey(
      String kmsKeyId, Long operatorUserId, String walletAlias, String walletAddress) {
    if (kmsKeyId == null) {
      return;
    }
    try {
      kmsKeyLifecyclePort.disableKey(kmsKeyId);
    } catch (RuntimeException ex) {
      log.warn("Cleanup disableKey failed for kmsKeyId={}", kmsKeyId, ex);
      kmsAuditRecorder.record(
          operatorUserId,
          walletAlias,
          kmsKeyId,
          walletAddress,
          KmsAuditAction.KMS_DISABLE,
          false,
          ex.getClass().getSimpleName());
    }
    try {
      kmsKeyLifecyclePort.scheduleKeyDeletion(kmsKeyId, CLEANUP_PENDING_WINDOW_DAYS);
    } catch (RuntimeException ex) {
      log.warn("Cleanup scheduleKeyDeletion failed for kmsKeyId={}", kmsKeyId, ex);
      kmsAuditRecorder.record(
          operatorUserId,
          walletAlias,
          kmsKeyId,
          walletAddress,
          KmsAuditAction.KMS_SCHEDULE_DELETION,
          false,
          ex.getClass().getSimpleName());
    }
  }

  private static byte[] decodePrivateKey(String rawPrivateKey) {
    String normalized = rawPrivateKey.trim().toLowerCase(java.util.Locale.ROOT);
    if (normalized.startsWith("0x")) {
      normalized = normalized.substring(2);
    }
    java.math.BigInteger value = new java.math.BigInteger(normalized, 16);
    byte[] padded = new byte[RAW_PRIVATE_KEY_BYTES];
    byte[] valueBytes = value.toByteArray();
    int srcOffset =
        valueBytes.length > RAW_PRIVATE_KEY_BYTES ? valueBytes.length - RAW_PRIVATE_KEY_BYTES : 0;
    int dstOffset = RAW_PRIVATE_KEY_BYTES - (valueBytes.length - srcOffset);
    System.arraycopy(valueBytes, srcOffset, padded, dstOffset, valueBytes.length - srcOffset);
    return padded;
  }

  private static void zeroize(byte[] sensitive) {
    if (sensitive != null) {
      java.util.Arrays.fill(sensitive, (byte) 0);
    }
  }
}
