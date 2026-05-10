package momzzangseven.mztkbe.global.error.user;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class UserBlockedException extends BusinessException {

  public UserBlockedException() {
    super(ErrorCode.USER_BLOCKED);
  }

  public UserBlockedException(String message) {
    super(ErrorCode.USER_BLOCKED, message);
  }
}
