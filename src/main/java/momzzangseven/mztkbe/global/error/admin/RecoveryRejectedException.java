package momzzangseven.mztkbe.global.error.admin;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Thrown when a recovery anchor does not match. */
public class RecoveryRejectedException extends BusinessException {

  public RecoveryRejectedException() {
    super(ErrorCode.RECOVERY_REJECTED);
  }
}
