package momzzangseven.mztkbe.global.error.admin;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Thrown when a manually set admin password does not meet the policy requirements. */
public class WeakAdminPasswordException extends BusinessException {

  public WeakAdminPasswordException(String message) {
    super(ErrorCode.WEAK_ADMIN_PASSWORD, message);
  }
}
