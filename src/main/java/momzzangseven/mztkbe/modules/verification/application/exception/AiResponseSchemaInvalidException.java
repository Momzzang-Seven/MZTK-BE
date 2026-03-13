package momzzangseven.mztkbe.modules.verification.application.exception;

public class AiResponseSchemaInvalidException extends RuntimeException {

  public AiResponseSchemaInvalidException(String message) {
    super(message);
  }

  public AiResponseSchemaInvalidException(String message, Throwable cause) {
    super(message, cause);
  }
}
