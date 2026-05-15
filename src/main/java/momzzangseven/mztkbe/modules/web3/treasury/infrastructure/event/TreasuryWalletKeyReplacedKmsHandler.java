package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.service.KmsAuditRecorder;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletKeyReplacedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AFTER_COMMIT handler for the ReplaceKey action (MOM-444 C6–C9). Performs, in order:
 *
 * <ol>
 *   <li>{@code updateAlias(walletAlias, newKmsKeyId)} — re-target the KMS alias.
 *   <li>If {@code event.disposeOldKey() == true}: {@code disableKey(oldKmsKeyId)} + {@code
 *       scheduleKeyDeletion(oldKmsKeyId, 7)}. For ARCHIVED sources ({@code disposeOldKey == false})
 *       we skip these because the old key is already pending deletion in KMS (set by the archive
 *       flow).
 * </ol>
 *
 * <p>Each KMS call is wrapped in independent try/catch — a failed updateAlias must not block the
 * subsequent oldKey disposal, since they target different keys. Failures land in {@code
 * web3_treasury_kms_audits}; the handler itself never propagates.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TreasuryWalletKeyReplacedKmsHandler {

  private static final int DELETION_PENDING_WINDOW_DAYS = 7;

  private final KmsKeyLifecyclePort kmsKeyLifecyclePort;
  private final KmsAuditRecorder kmsAuditRecorder;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(TreasuryWalletKeyReplacedEvent event) {
    safelyUpdateAlias(event);
    if (event.disposeOldKey()) {
      safelyDisableOldKey(event);
      safelyScheduleOldKeyDeletion(event);
    }
  }

  private void safelyUpdateAlias(TreasuryWalletKeyReplacedEvent event) {
    try {
      kmsKeyLifecyclePort.updateAlias(event.walletAlias(), event.newKmsKeyId());
      kmsAuditRecorder.record(
          event.operatorUserId(),
          event.walletAlias(),
          event.newKmsKeyId(),
          event.walletAddress(),
          KmsAuditAction.KMS_UPDATE_ALIAS,
          true,
          null);
    } catch (RuntimeException ex) {
      log.warn(
          "KMS updateAlias failed post-commit for alias={} (newKey={}); audit row recorded",
          event.walletAlias(),
          event.newKmsKeyId(),
          ex);
      kmsAuditRecorder.record(
          event.operatorUserId(),
          event.walletAlias(),
          event.newKmsKeyId(),
          event.walletAddress(),
          KmsAuditAction.KMS_UPDATE_ALIAS,
          false,
          ex.getClass().getSimpleName());
    }
  }

  private void safelyDisableOldKey(TreasuryWalletKeyReplacedEvent event) {
    try {
      kmsKeyLifecyclePort.disableKey(event.oldKmsKeyId());
      kmsAuditRecorder.record(
          event.operatorUserId(),
          event.walletAlias(),
          event.oldKmsKeyId(),
          event.walletAddress(),
          KmsAuditAction.KMS_DISABLE,
          true,
          null);
    } catch (RuntimeException ex) {
      log.warn(
          "KMS disableKey failed post-commit for old key={} (alias={}); audit row recorded",
          event.oldKmsKeyId(),
          event.walletAlias(),
          ex);
      kmsAuditRecorder.record(
          event.operatorUserId(),
          event.walletAlias(),
          event.oldKmsKeyId(),
          event.walletAddress(),
          KmsAuditAction.KMS_DISABLE,
          false,
          ex.getClass().getSimpleName());
    }
  }

  private void safelyScheduleOldKeyDeletion(TreasuryWalletKeyReplacedEvent event) {
    try {
      kmsKeyLifecyclePort.scheduleKeyDeletion(event.oldKmsKeyId(), DELETION_PENDING_WINDOW_DAYS);
      kmsAuditRecorder.record(
          event.operatorUserId(),
          event.walletAlias(),
          event.oldKmsKeyId(),
          event.walletAddress(),
          KmsAuditAction.KMS_SCHEDULE_DELETION,
          true,
          null);
    } catch (RuntimeException ex) {
      log.warn(
          "KMS scheduleKeyDeletion failed post-commit for old key={} (alias={}); audit row recorded",
          event.oldKmsKeyId(),
          event.walletAlias(),
          ex);
      kmsAuditRecorder.record(
          event.operatorUserId(),
          event.walletAlias(),
          event.oldKmsKeyId(),
          event.walletAddress(),
          KmsAuditAction.KMS_SCHEDULE_DELETION,
          false,
          ex.getClass().getSimpleName());
    }
  }
}
