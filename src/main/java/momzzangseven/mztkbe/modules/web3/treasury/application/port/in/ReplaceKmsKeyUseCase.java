package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ReplaceKmsKeyCommand;

/**
 * Drives the KMS side effects for the ReplaceKey action (MOM-444 C6–C9). Performs {@code
 * updateAlias} to retarget the alias at the new key, then — when {@code disposeOldKey} is true —
 * disables and schedules deletion of the old key. Each KMS call writes an audit row to {@code
 * web3_treasury_kms_audits}. Invoked by an AFTER_COMMIT event handler so the wallet row's new key
 * has already been persisted before this runs.
 *
 * <p>Idempotent on stale events: re-reads the current treasury_wallets row under {@code
 * PESSIMISTIC_WRITE} lock and CAS-checks that {@code current.kmsKeyId} still equals {@code
 * command.newKmsKeyId()} and {@code status} is still {@code ACTIVE}. If the row has moved on (a
 * later rotation, disable, or archive), records a {@code KMS_REPLACE_SKIPPED} audit row
 * (success=true) with a {@code ROW_MISSING / KEY_NULL / KEY_ID_MISMATCH / STATUS_MISMATCH} reason
 * and returns without invoking AWS KMS. When the stale event still holds {@code disposeOldKey=true}
 * and its {@code oldKmsKeyId} is no longer the current row's key, the implementation performs a
 * best-effort {@code disableKey + scheduleKeyDeletion} on the orphaned old key to prevent leaks in
 * long rotation chains.
 */
public interface ReplaceKmsKeyUseCase {

  void execute(ReplaceKmsKeyCommand command);
}
