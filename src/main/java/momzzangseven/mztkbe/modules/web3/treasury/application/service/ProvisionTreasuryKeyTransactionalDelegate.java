package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAlreadyProvisionedException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
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
 * Transactional inner stage of the MOM-444 3-phase provisioning flow.
 *
 * <p>Extracted from {@link ProvisionTreasuryKeyService} into its own bean so Spring AOP can apply
 * {@link Transactional} to {@link #lockedCommit} when the outer service calls it (self-invocation
 * would bypass the proxy and run un-transactional). Holds the DB-only critical section: acquires a
 * {@code PESSIMISTIC_WRITE} row lock on {@code wallet_alias}, dispatches to one of the five MOM-444
 * actions, persists the row, and publishes the AFTER_COMMIT event. KMS mutations all happen outside
 * this method — the only KMS call retained inside the transaction is the read-only {@code
 * describeAliasTarget} on the IdempotentRetry (C4) path, which must observe alias state under lock
 * to race-freely choose between {@code ALREADY_PROVISIONED} and alias-repair.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProvisionTreasuryKeyTransactionalDelegate {

  private static final int CLEANUP_PENDING_WINDOW_DAYS = 7;

  private final LoadTreasuryWalletPort loadTreasuryWalletPort;
  private final SaveTreasuryWalletPort saveTreasuryWalletPort;
  private final KmsKeyLifecyclePort kmsKeyLifecyclePort;
  private final TreasuryAuditRecorder treasuryAuditRecorder;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final Clock clock;

  /**
   * Acquire row lock, dispatch to one of five actions, save and publish event. Caller is
   * responsible for cleaning up {@code preMintedKeyId} when {@code attachedFlag} stays false on
   * return.
   */
  @Transactional
  public ProvisionTreasuryKeyResult lockedCommit(
      ProvisionTreasuryKeyCommand command,
      String derivedAddress,
      String preMintedKeyId,
      AtomicBoolean attachedFlag,
      AtomicBoolean cleanupInvoked) {

    TreasuryRole role = command.role();
    String walletAlias = role.toAlias();
    Optional<TreasuryWallet> existingOpt = loadTreasuryWalletPort.loadByAliasForUpdate(walletAlias);

    if (existingOpt.isEmpty()) {
      return freshProvision(
          command, walletAlias, role, derivedAddress, preMintedKeyId, attachedFlag, cleanupInvoked);
    }

    TreasuryWallet existing = existingOpt.get();
    boolean addressMatches = derivedAddress.equalsIgnoreCase(existing.getWalletAddress());

    if (existing.getKmsKeyId() == null) {
      return backfill(
          command, role, existing, derivedAddress, preMintedKeyId, attachedFlag, cleanupInvoked);
    }

    if (addressMatches) {
      switch (existing.getStatus()) {
        case ACTIVE:
          return handleExistingProvisionedRow(command, existing, derivedAddress, role);
        case DISABLED:
          return reEnableSameKey(command, existing, role);
        case ARCHIVED:
          return replaceKey(
              command,
              role,
              existing,
              derivedAddress,
              preMintedKeyId,
              false,
              attachedFlag,
              cleanupInvoked);
        default:
          throw new IllegalStateException("Unsupported status: " + existing.getStatus());
      }
    }

    boolean disposeOldKey = existing.getStatus() != TreasuryWalletStatus.ARCHIVED;
    return replaceKey(
        command,
        role,
        existing,
        derivedAddress,
        preMintedKeyId,
        disposeOldKey,
        attachedFlag,
        cleanupInvoked);
  }

  private ProvisionTreasuryKeyResult freshProvision(
      ProvisionTreasuryKeyCommand command,
      String walletAlias,
      TreasuryRole role,
      String derivedAddress,
      String preMintedKeyId,
      AtomicBoolean attachedFlag,
      AtomicBoolean cleanupInvoked) {
    registerCleanupOnRollback(
        preMintedKeyId, cleanupInvoked, command.operatorUserId(), derivedAddress);
    TreasuryWallet wallet =
        TreasuryWallet.provision(walletAlias, preMintedKeyId, derivedAddress, role, clock);
    TreasuryWallet saved = saveTreasuryWalletPort.save(wallet);
    attachedFlag.set(true);
    applicationEventPublisher.publishEvent(
        new TreasuryWalletProvisionedEvent(
            walletAlias, preMintedKeyId, derivedAddress, command.operatorUserId(), false));
    return ProvisionTreasuryKeyResult.from(saved, role);
  }

  private ProvisionTreasuryKeyResult backfill(
      ProvisionTreasuryKeyCommand command,
      TreasuryRole role,
      TreasuryWallet existing,
      String derivedAddress,
      String preMintedKeyId,
      AtomicBoolean attachedFlag,
      AtomicBoolean cleanupInvoked) {
    registerCleanupOnRollback(
        preMintedKeyId, cleanupInvoked, command.operatorUserId(), derivedAddress);
    TreasuryWallet wallet =
        TreasuryWallet.backfill(existing, preMintedKeyId, derivedAddress, clock);
    TreasuryWallet saved = saveTreasuryWalletPort.save(wallet);
    attachedFlag.set(true);
    applicationEventPublisher.publishEvent(
        new TreasuryWalletProvisionedEvent(
            existing.getWalletAlias(),
            preMintedKeyId,
            derivedAddress,
            command.operatorUserId(),
            false));
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
      String preMintedKeyId,
      boolean disposeOldKey,
      AtomicBoolean attachedFlag,
      AtomicBoolean cleanupInvoked) {
    registerCleanupOnRollback(
        preMintedKeyId, cleanupInvoked, command.operatorUserId(), derivedAddress);
    TreasuryWallet rotated =
        TreasuryWallet.replaceKey(existing, preMintedKeyId, derivedAddress, clock);
    TreasuryWallet saved = saveTreasuryWalletPort.save(rotated);
    attachedFlag.set(true);
    applicationEventPublisher.publishEvent(
        new TreasuryWalletKeyReplacedEvent(
            existing.getWalletAlias(),
            existing.getKmsKeyId(),
            preMintedKeyId,
            derivedAddress,
            command.operatorUserId(),
            disposeOldKey));
    return ProvisionTreasuryKeyResult.from(saved, role);
  }

  /**
   * C4 dispatch: alias가 ENABLED 면 {@code ALREADY_PROVISIONED}, 그 외(DISABLED / PENDING_DELETION /
   * UNAVAILABLE) 면 alias-repair 이벤트 재발행. {@code describeAliasTarget} 는 read-only KMS API 로, MOM-444
   * §4.0.2 의 "TX 안에 잔존하는 KMS 호출" 예외 항목.
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
                cleanupInvoked.set(true);
                treasuryAuditRecorder.record(
                    operatorUserId, derivedAddress, false, "TX_STATUS_UNKNOWN");
            }
          }
        });
  }

  void cleanupKmsKey(String kmsKeyId) {
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
}
