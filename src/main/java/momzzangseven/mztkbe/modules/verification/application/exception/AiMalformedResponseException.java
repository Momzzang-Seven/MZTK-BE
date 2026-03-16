package momzzangseven.mztkbe.modules.verification.application.exception;

public class AiMalformedResponseException extends RuntimeException {

  public AiMalformedResponseException(String message, Throwable cause) {
    super(message, cause);
  }

  public AiMalformedResponseException(String message) {
    super(message);
  }
}
