package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ReplaceKmsKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ReplaceKmsKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import org.springframework.stereotype.Service;

/**
 * MOM-444 ReplaceKey AFTER_COMMIT KMS orchestration. Three steps in order:
 *
 * <ol>
 *   <li>{@code updateAlias(walletAlias, newKmsKeyId)} — retarget the alias at the rotated key.
 *   <li>If {@code disposeOldKey} is true: {@code disableKey(oldKmsKeyId)}.
 *   <li>If {@code disposeOldKey} is true: {@code scheduleKeyDeletion(oldKmsKeyId, 7)}.
 * </ol>
 *
 * <p>Each KMS call is wrapped in independent try/catch — a failed updateAlias must not block the
 * subsequent old-key disposal because they target different keys. Failures land in {@code
 * web3_treasury_kms_audits} as a {@code KMS_*} failure row but do not propagate.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReplaceKmsKeyService implements ReplaceKmsKeyUseCase {

  private static final int DELETION_PENDING_WINDOW_DAYS = 7;

  private final KmsKeyLifecyclePort kmsKeyLifecyclePort;
  private final KmsAuditRecorder kmsAuditRecorder;

  @Override
  public void execute(ReplaceKmsKeyCommand command) {
    safelyUpdateAlias(command);
    if (command.disposeOldKey()) {
      safelyDisableOldKey(command);
      safelyScheduleOldKeyDeletion(command);
    }
  }

  private void safelyUpdateAlias(ReplaceKmsKeyCommand command) {
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
      log.warn(
          "KMS updateAlias failed post-commit for alias={} (newKey={}); audit row recorded",
          command.walletAlias(),
          command.newKmsKeyId(),
          ex);
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.newKmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_UPDATE_ALIAS,
          false,
          ex.getClass().getSimpleName());
    }
  }

  private void safelyDisableOldKey(ReplaceKmsKeyCommand command) {
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
      log.warn(
          "KMS disableKey failed post-commit for old key={} (alias={}); audit row recorded",
          command.oldKmsKeyId(),
          command.walletAlias(),
          ex);
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.oldKmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_DISABLE,
          false,
          ex.getClass().getSimpleName());
    }
  }

  private void safelyScheduleOldKeyDeletion(ReplaceKmsKeyCommand command) {
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
      log.warn(
          "KMS scheduleKeyDeletion failed post-commit for old key={} (alias={}); audit row recorded",
          command.oldKmsKeyId(),
          command.walletAlias(),
          ex);
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.oldKmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_SCHEDULE_DELETION,
          false,
          ex.getClass().getSimpleName());
    }
  }
}
