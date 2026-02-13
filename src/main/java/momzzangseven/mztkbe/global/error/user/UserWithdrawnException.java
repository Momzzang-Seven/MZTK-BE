package momzzangseven.mztkbe.global.error.user;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when an operation is attempted on a withdrawn (soft-deleted) user. */
public class UserWithdrawnException extends BusinessException {

  public UserWithdrawnException() {
    super(ErrorCode.USER_WITHDRAWN);
  }

  public UserWithdrawnException(String message) {
    super(ErrorCode.USER_WITHDRAWN, message);
  }
}
