package momzzangseven.mztkbe.global.error;

/** Exception thrown when password doesn't meet requirements. */
public class InvalidPasswordException extends BusinessException {

  public InvalidPasswordException() {
    super(ErrorCode.INVALID_PASSWORD);
  }

  public InvalidPasswordException(String customMessage) {
    super(ErrorCode.INVALID_PASSWORD, customMessage);
  }
}
