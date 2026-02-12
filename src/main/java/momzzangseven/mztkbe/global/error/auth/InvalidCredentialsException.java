package momzzangseven.mztkbe.global.error.auth;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when login credentials are invalid. */
public class InvalidCredentialsException extends BusinessException {

  public InvalidCredentialsException() {
    super(ErrorCode.INVALID_CREDENTIALS);
  }

  public InvalidCredentialsException(String customMessage) {
    super(ErrorCode.INVALID_CREDENTIALS, customMessage);
  }
}
