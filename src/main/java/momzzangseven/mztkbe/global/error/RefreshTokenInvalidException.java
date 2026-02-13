package momzzangseven.mztkbe.global.error;

/** Thrown when a refresh token is expired or revoked. */
public class RefreshTokenInvalidException extends TokenException {

  /** Create exception with contextual information. */
  public RefreshTokenInvalidException(String detail) {
    super(
        ErrorCode.REFRESH_TOKEN_INVALID,
        ErrorCode.REFRESH_TOKEN_INVALID.getMessage() + ": " + detail);
  }
}
