package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.BindKmsAliasCommand;

/**
 * Idempotently binds a KMS alias to a key id and records the outcome to {@code
 * web3_treasury_kms_audits}. Called by an AFTER_COMMIT event handler so the DB row's {@code
 * kms_key_id} has already been persisted before this runs. Recovers stale aliases pointing to
 * {@code PENDING_DELETION} / {@code DISABLED} keys via {@code UpdateAlias}; surfaces an {@code
 * ALREADY_PROVISIONED} error for an out-of-band ENABLED conflict.
 */
public interface BindKmsAliasUseCase {

  void execute(BindKmsAliasCommand command);
}
