package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ReplaceKmsKeyCommand;

/**
 * Drives the KMS side effects for the ReplaceKey action (MOM-444 C6–C9). Performs {@code
 * updateAlias} to retarget the alias at the new key, then — when {@code disposeOldKey} is true —
 * disables and schedules deletion of the old key. Each KMS call writes an audit row to {@code
 * web3_treasury_kms_audits}. Invoked by an AFTER_COMMIT event handler so the wallet row's new key
 * has already been persisted before this runs.
 */
public interface ReplaceKmsKeyUseCase {

  void execute(ReplaceKmsKeyCommand command);
}
