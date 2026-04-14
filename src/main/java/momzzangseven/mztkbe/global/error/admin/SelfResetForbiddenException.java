package momzzangseven.mztkbe.global.error.admin;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Thrown when an admin attempts to peer-reset their own password. */
public class SelfResetForbiddenException extends BusinessException {

  public SelfResetForbiddenException() {
    super(ErrorCode.SELF_RESET_FORBIDDEN);
  }
}
