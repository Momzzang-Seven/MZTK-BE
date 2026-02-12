package momzzangseven.mztkbe.global.error.token;

import momzzangseven.mztkbe.global.error.code.ErrorCode;
import org.springframework.http.HttpStatus;

/** Base exception for token-related errors with an associated ErrorCode. */
public class TokenException extends RuntimeException {

  private final ErrorCode errorCode;

  /**
   * Constructor with ErrorCode and custom message. Use this when you want to add context to the
   * default message.
   *
   * @param errorCode The error code
   * @param customMessage Custom message (can be null to use default)
   */
  public TokenException(ErrorCode errorCode, String customMessage) {
    super(customMessage != null ? customMessage : errorCode.getMessage());
    this.errorCode = errorCode;
  }

  /**
   * Constructor with only ErrorCode. Uses the default message from ErrorCode.
   *
   * @param errorCode The error code
   */
  public TokenException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  /**
   * Constructor with ErrorCode, custom message, and cause.
   *
   * @param errorCode The error code
   * @param customMessage Custom message
   * @param cause The underlying cause
   */
  public TokenException(ErrorCode errorCode, String customMessage, Throwable cause) {
    super(customMessage != null ? customMessage : errorCode.getMessage(), cause);
    this.errorCode = errorCode;
  }

  /**
   * Get the error code string.
   *
   * @return Error code (e.g., "AUTH_001")
   */
  public String getCode() {
    return errorCode.getCode();
  }

  /**
   * Get the HTTP status code.
   *
   * @return HTTP status code from ErrorCode
   */
  public HttpStatus getHttpStatus() {
    return errorCode.getHttpStatus();
  }
}
