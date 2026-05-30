package momzzangseven.mztkbe.global.error.user;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when an operation is attempted on an unverified (email/identity) user. */
public class UserUnverifiedException extends BusinessException {

  public UserUnverifiedException() {
    super(ErrorCode.USER_UNVERIFIED);
  }

  public UserUnverifiedException(String message) {
    super(ErrorCode.USER_UNVERIFIED, message);
  }
}
