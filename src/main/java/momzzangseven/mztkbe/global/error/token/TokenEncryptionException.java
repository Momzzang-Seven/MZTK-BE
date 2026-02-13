package momzzangseven.mztkbe.global.error.token;

import momzzangseven.mztkbe.global.error.ErrorCode;

/** Thrown when encrypting/decrypting a sensitive token fails due to cryptographic issues. */
public class TokenEncryptionException extends TokenException {

  public TokenEncryptionException() {
    super(ErrorCode.TOKEN_ENCRYPTION_FAILED);
  }

  public TokenEncryptionException(String message) {
    super(ErrorCode.TOKEN_ENCRYPTION_FAILED, message);
  }

  public TokenEncryptionException(String message, Throwable cause) {
    super(ErrorCode.TOKEN_ENCRYPTION_FAILED, message, cause);
  }
}
