package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.EnableKmsKeyCommand;

/**
 * Calls {@code KmsKeyLifecyclePort.enableKey} and records the outcome to {@code
 * web3_treasury_kms_audits}. Invoked by an AFTER_COMMIT event handler for the ReEnableSameKey
 * action (MOM-444 C5) so the wallet row's {@code ACTIVE} state has already been persisted before
 * this runs.
 */
public interface EnableKmsKeyUseCase {

  void execute(EnableKmsKeyCommand command);
}
