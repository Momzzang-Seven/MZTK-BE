package momzzangseven.mztkbe.global.error.admin;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Thrown when the recovery anchor service is unavailable. */
public class RecoveryAnchorUnavailableException extends BusinessException {

  public RecoveryAnchorUnavailableException(String message) {
    super(ErrorCode.RECOVERY_ANCHOR_UNAVAILABLE, message);
  }

  public RecoveryAnchorUnavailableException(String message, Throwable cause) {
    super(ErrorCode.RECOVERY_ANCHOR_UNAVAILABLE, message, cause);
  }
}
