package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.DisableKmsKeyCommand;

/**
 * Calls {@code KmsKeyLifecyclePort.disableKey} and records the outcome to
 * {@code web3_treasury_kms_audits}. Invoked by an AFTER_COMMIT event handler so the DB row's
 * {@code DISABLED} state has already been persisted before this runs.
 */
public interface DisableKmsKeyUseCase {

  void execute(DisableKmsKeyCommand command);
}
