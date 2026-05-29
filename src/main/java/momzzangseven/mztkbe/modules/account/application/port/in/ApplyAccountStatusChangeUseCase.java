package momzzangseven.mztkbe.modules.account.application.port.in;

import momzzangseven.mztkbe.modules.account.application.dto.ApplyAccountStatusChangeCommand;

/**
 * Input port that idempotently applies a single account-status change to the in-memory denylist:
 * put for a non-ACTIVE status, evict for ACTIVE or a {@code null} status (hard delete).
 */
public interface ApplyAccountStatusChangeUseCase {

  /**
   * Idempotently applies the given status change to the denylist.
   *
   * @param command the user identifier and its (possibly {@code null}) new status
   */
  void execute(ApplyAccountStatusChangeCommand command);
}
