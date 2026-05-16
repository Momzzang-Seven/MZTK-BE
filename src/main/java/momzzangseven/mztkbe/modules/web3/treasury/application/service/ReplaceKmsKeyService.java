package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ReplaceKmsKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ReplaceKmsKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * MOM-444 ReplaceKey AFTER_COMMIT KMS orchestration. Three steps in order:
 *
 * <ol>
 *   <li>{@code updateAlias(walletAlias, newKmsKeyId)} — retarget the alias at the rotated key.
 *   <li>If {@code disposeOldKey} is true: {@code disableKey(oldKmsKeyId)}.
 *   <li>If {@code disposeOldKey} is true: {@code scheduleKeyDeletion(oldKmsKeyId, 7)}.
 * </ol>
 *
 * <p>Idempotent on stale events: opens a fresh {@code REQUIRES_NEW} transaction, re-reads the
 * {@code treasury_wallets} row under {@code PESSIMISTIC_WRITE} (CAS-checks the current row), and
 * only invokes AWS KMS when the row still matches the command's expected state ({@code kmsKeyId ==
 * newKmsKeyId}, {@code status == ACTIVE}). On CAS miss, records a {@code KMS_REPLACE_SKIPPED} audit
 * row with a fine-grained reason ({@code ROW_MISSING} / {@code KEY_NULL} / {@code KEY_ID_MISMATCH}
 * / {@code STATUS_MISMATCH}) and returns without touching KMS. When the stale skip is observed and
 * the command's {@code oldKmsKeyId} is no longer the current DB key, a best-effort {@code
 * disableKey + scheduleKeyDeletion} pass is run on the orphan old key (idempotent and
 * swallow-on-throw, because no other handler will dispose it).
 *
 * <p>Each KMS call in the happy path is wrapped in independent try/catch. The audit row lands in
 * {@code web3_treasury_kms_audits}; on failure the exception is rethrown so the caller's
 * post-commit boundary surfaces it.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReplaceKmsKeyService implements ReplaceKmsKeyUseCase {

  private static final int DELETION_PENDING_WINDOW_DAYS = 7;

  private final KmsKeyLifecyclePort kmsKeyLifecyclePort;
  private final KmsAuditRecorder kmsAuditRecorder;
  private final LoadTreasuryWalletPort loadTreasuryWalletPort;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void execute(ReplaceKmsKeyCommand command) {
    Optional<TreasuryWallet> currentOpt =
        loadTreasuryWalletPort.loadByAliasForUpdate(command.walletAlias());

    String staleReason = detectStaleReason(currentOpt, command);
    if (staleReason != null) {
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.newKmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_REPLACE_SKIPPED,
          true,
          staleReason);

      if (command.disposeOldKey()
          && command.oldKmsKeyId() != null
          && (currentOpt.isEmpty()
              || !command.oldKmsKeyId().equals(currentOpt.get().getKmsKeyId()))) {
        disposeOrphanOldKeyBestEffort(command);
      }
      return;
    }

    updateAliasOrThrow(command);
    if (command.disposeOldKey()) {
      disableOldKeyOrThrow(command);
      scheduleOldKeyDeletionOrThrow(command);
    }
  }

  private String detectStaleReason(
      Optional<TreasuryWallet> currentOpt, ReplaceKmsKeyCommand command) {
    if (currentOpt.isEmpty()) {
      return "ROW_MISSING";
    }
    TreasuryWallet current = currentOpt.get();
    if (current.getKmsKeyId() == null) {
      return "KEY_NULL";
    }
    if (!command.newKmsKeyId().equals(current.getKmsKeyId())) {
      return "KEY_ID_MISMATCH";
    }
    if (current.getStatus() != TreasuryWalletStatus.ACTIVE) {
      return "STATUS_MISMATCH";
    }
    return null;
  }

  private void updateAliasOrThrow(ReplaceKmsKeyCommand command) {
    try {
      kmsKeyLifecyclePort.updateAlias(command.walletAlias(), command.newKmsKeyId());
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.newKmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_UPDATE_ALIAS,
          true,
          null);
    } catch (RuntimeException ex) {
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.newKmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_UPDATE_ALIAS,
          false,
          ex.getClass().getSimpleName());
      throw ex;
    }
  }

  private void disableOldKeyOrThrow(ReplaceKmsKeyCommand command) {
    try {
      kmsKeyLifecyclePort.disableKey(command.oldKmsKeyId());
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.oldKmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_DISABLE,
          true,
          null);
    } catch (RuntimeException ex) {
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.oldKmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_DISABLE,
          false,
          ex.getClass().getSimpleName());
      throw ex;
    }
  }

  private void scheduleOldKeyDeletionOrThrow(ReplaceKmsKeyCommand command) {
    try {
      kmsKeyLifecyclePort.scheduleKeyDeletion(command.oldKmsKeyId(), DELETION_PENDING_WINDOW_DAYS);
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.oldKmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_SCHEDULE_DELETION,
          true,
          null);
    } catch (RuntimeException ex) {
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.oldKmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_SCHEDULE_DELETION,
          false,
          ex.getClass().getSimpleName());
      throw ex;
    }
  }

  private void disposeOrphanOldKeyBestEffort(ReplaceKmsKeyCommand command) {
    try {
      kmsKeyLifecyclePort.disableKey(command.oldKmsKeyId());
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.oldKmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_DISABLE,
          true,
          "ORPHAN_FROM_STALE_REPLACE");
    } catch (RuntimeException ex) {
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.oldKmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_DISABLE,
          false,
          "ORPHAN_FROM_STALE_REPLACE:" + ex.getClass().getSimpleName());
    }
    try {
      kmsKeyLifecyclePort.scheduleKeyDeletion(command.oldKmsKeyId(), DELETION_PENDING_WINDOW_DAYS);
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.oldKmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_SCHEDULE_DELETION,
          true,
          "ORPHAN_FROM_STALE_REPLACE");
    } catch (RuntimeException ex) {
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.oldKmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_SCHEDULE_DELETION,
          false,
          "ORPHAN_FROM_STALE_REPLACE:" + ex.getClass().getSimpleName());
      // intentional swallow — we are already in stale-skip path
    }
  }
}
