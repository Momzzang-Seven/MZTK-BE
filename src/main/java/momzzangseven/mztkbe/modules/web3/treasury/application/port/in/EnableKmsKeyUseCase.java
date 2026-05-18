package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.EnableKmsKeyCommand;

/**
 * Calls {@code KmsKeyLifecyclePort.enableKey} and records the outcome to {@code
 * web3_treasury_kms_audits}. Invoked by an AFTER_COMMIT event handler for the ReEnableSameKey
 * action (MOM-444 C5) so the wallet row's {@code ACTIVE} state has already been persisted before
 * this runs.
 *
 * <p>Idempotent on stale events: re-reads the current treasury_wallets row under {@code
 * PESSIMISTIC_WRITE} lock and CAS-checks that {@code current.kmsKeyId} still equals {@code
 * command.kmsKeyId()} and {@code status} is still {@code ACTIVE}. If the row has moved on (a later
 * rotation, disable, or archive), records a {@code KMS_ENABLE_SKIPPED} audit row (success=true)
 * with a {@code ROW_MISSING / KEY_ID_MISMATCH / STATUS_MISMATCH} reason and returns without
 * invoking AWS KMS.
 */
public interface EnableKmsKeyUseCase {

  void execute(EnableKmsKeyCommand command);
}
