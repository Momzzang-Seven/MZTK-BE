package momzzangseven.mztkbe.global.error.token;

import momzzangseven.mztkbe.global.error.ErrorCode;

/**
 * Thrown when a refresh token cannot be located in the database.
 */
public class RefreshTokenNotFoundException extends TokenException {

  /**
   * Create exception indicating a token was not found.
   */
  public RefreshTokenNotFoundException() {
    super(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
  }

  /** Include details about the missing token. */
  public RefreshTokenNotFoundException(String detail) {
    super(
        ErrorCode.REFRESH_TOKEN_NOT_FOUND,
        ErrorCode.REFRESH_TOKEN_NOT_FOUND.getMessage() + ": " + detail);
  }
}
