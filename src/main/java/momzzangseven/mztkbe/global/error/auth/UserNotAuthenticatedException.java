package momzzangseven.mztkbe.global.error.auth;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/**
 * Exception thrown when an unauthenticated user attempts to access a protected resource.
 *
 * <p>This exception is thrown when: - No authentication information is found in SecurityContext -
 * Authentication principal is invalid or missing - User attempts to access authenticated endpoints
 * without a valid token
 *
 * <p>HTTP Status: 401 Unauthorized
 *
 * <p>Error Code: AUTH_006
 */
public class UserNotAuthenticatedException extends BusinessException {

  /** Default constructor with standard message. */
  public UserNotAuthenticatedException() {
    super(ErrorCode.USER_NOT_AUTHENTICATED);
  }

  /**
   * Constructor with custom message for additional context.
   *
   * @param customMessage Additional context about why authentication failed
   */
  public UserNotAuthenticatedException(String customMessage) {
    super(ErrorCode.USER_NOT_AUTHENTICATED, customMessage);
  }

  /**
   * Constructor with custom message and underlying cause.
   *
   * @param customMessage Additional context
   * @param cause The underlying exception that caused authentication failure
   */
  public UserNotAuthenticatedException(String customMessage, Throwable cause) {
    super(ErrorCode.USER_NOT_AUTHENTICATED, customMessage, cause);
  }
}
