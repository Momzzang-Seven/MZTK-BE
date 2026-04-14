package momzzangseven.mztkbe.global.error.user;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Thrown when a user attempts to grant themselves ADMIN privileges. */
public class IllegalAdminGrantException extends BusinessException {

  public IllegalAdminGrantException() {
    super(ErrorCode.ILLEGAL_ADMIN_GRANT, ErrorCode.ILLEGAL_ADMIN_GRANT.getMessage());
  }

  public IllegalAdminGrantException(String message) {
    super(ErrorCode.ILLEGAL_ADMIN_GRANT, message);
  }
}
