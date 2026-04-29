package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ScheduleKmsKeyDeletionCommand;

/**
 * Calls {@code KmsKeyLifecyclePort.scheduleKeyDeletion} and records the outcome to
 * {@code web3_treasury_kms_audits}. Invoked by an AFTER_COMMIT event handler so the DB row's
 * {@code ARCHIVED} state has already been persisted before this runs.
 */
public interface ScheduleKmsKeyDeletionUseCase {

  void execute(ScheduleKmsKeyDeletionCommand command);
}
