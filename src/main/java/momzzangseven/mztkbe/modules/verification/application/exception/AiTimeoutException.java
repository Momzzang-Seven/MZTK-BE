package momzzangseven.mztkbe.modules.verification.application.exception;

public class AiTimeoutException extends RuntimeException {

  public AiTimeoutException(String message) {
    super(message);
  }

  public AiTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
