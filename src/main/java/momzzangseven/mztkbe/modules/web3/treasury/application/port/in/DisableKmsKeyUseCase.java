package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.DisableKmsKeyCommand;

/**
 * Calls {@code KmsKeyLifecyclePort.disableKey} and records the outcome to {@code
 * web3_treasury_kms_audits}. Invoked by an AFTER_COMMIT event handler so the DB row's {@code
 * DISABLED} state has already been persisted before this runs.
 *
 * <p>Idempotent on stale events: implementations re-read the {@code treasury_wallets} row under
 * {@code PESSIMISTIC_WRITE}; if the current row's {@code (kmsKeyId, status=DISABLED)} no longer
 * matches the command, a {@code KMS_DISABLE_SKIPPED} audit row is written and the AWS KMS call is
 * skipped. The CAS gate blocks the reverse-order Disable/Reactivate race where a delayed Disable
 * handler would otherwise leave KMS DISABLED while the row sits ACTIVE after C5 reEnableSameKey.
 */
public interface DisableKmsKeyUseCase {

  void execute(DisableKmsKeyCommand command);
}
