package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.DisableKmsKeyCommand;

/**
 * Calls {@code KmsKeyLifecyclePort.disableKey} and records the outcome to {@code
 * web3_treasury_kms_audits}. Invoked by an AFTER_COMMIT event handler so the DB row's {@code
 * DISABLED} state has already been persisted before this runs.
 *
 * <p>No DB-row CAS gate: the command pins a specific {@code kmsKeyId} at issue time and {@code
 * disableKey} is KMS-side idempotent, so a stale event still expresses a valid intent ("disable
 * that key"). The one residual race — a late Disable firing after a subsequent Reactivate, which
 * would drift KMS to DISABLED while the row sits ACTIVE — is self-healed by R5 reactivation
 * recovery in {@code ProvisionTreasuryKeyTransactionalDelegate} on the next provision/probe.
 */
public interface DisableKmsKeyUseCase {

  void execute(DisableKmsKeyCommand command);
}
