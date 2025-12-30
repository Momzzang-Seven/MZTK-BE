package momzzangseven.mztkbe.global.error.user;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class InvalidUserRoleException extends BusinessException {

  public InvalidUserRoleException() {
    super(ErrorCode.INVALID_ROLE, ErrorCode.INVALID_ROLE.getMessage());
  }

  public InvalidUserRoleException(String message) {
    super(ErrorCode.INVALID_ROLE, ErrorCode.INVALID_ROLE.getMessage() + ": " + message);
  }
}
