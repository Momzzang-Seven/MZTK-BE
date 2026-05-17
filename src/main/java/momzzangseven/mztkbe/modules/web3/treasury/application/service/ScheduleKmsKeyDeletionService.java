package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ScheduleKmsKeyDeletionCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ScheduleKmsKeyDeletionUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import org.springframework.stereotype.Service;

/**
 * AFTER_COMMIT KMS orchestration for Archive. Thin wrapper over {@code scheduleKeyDeletion} +
 * audit; no stale-event CAS is required because {@code ARCHIVED} is terminal and the call's intent
 * is pinned to a specific {@code kmsKeyId} (see {@link ScheduleKmsKeyDeletionUseCase} Javadoc).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduleKmsKeyDeletionService implements ScheduleKmsKeyDeletionUseCase {

  private final KmsKeyLifecyclePort kmsKeyLifecyclePort;
  private final KmsAuditRecorder kmsAuditRecorder;

  @Override
  public void execute(ScheduleKmsKeyDeletionCommand command) {
    try {
      kmsKeyLifecyclePort.scheduleKeyDeletion(command.kmsKeyId(), command.pendingWindowDays());
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.kmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_SCHEDULE_DELETION,
          true,
          null);
    } catch (RuntimeException ex) {
      kmsAuditRecorder.record(
          command.operatorUserId(),
          command.walletAlias(),
          command.kmsKeyId(),
          command.walletAddress(),
          KmsAuditAction.KMS_SCHEDULE_DELETION,
          false,
          ex.getClass().getSimpleName());
      throw ex;
    }
  }
}
