package momzzangseven.mztkbe.global.error;

/** Thrown when a security violation is detected during token validation. */
public class TokenSecurityException extends TokenException {

  public TokenSecurityException() {
    super(ErrorCode.TOKEN_SECURITY_THREAT);
  }

  public TokenSecurityException(String message) {
    super(ErrorCode.TOKEN_SECURITY_THREAT, message);
  }
}
