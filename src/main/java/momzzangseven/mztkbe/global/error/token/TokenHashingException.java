package momzzangseven.mztkbe.global.error.token;

import momzzangseven.mztkbe.global.error.ErrorCode;

/**
 * Thrown when hashing a token value fails due to cryptographic issues.
 */
public class TokenHashingException extends TokenException {

    public TokenHashingException() {
        super(ErrorCode.TOKEN_HASHING_FAILED);
    }

    public TokenHashingException(String message) {
        super(ErrorCode.TOKEN_HASHING_FAILED, message);
    }

    public TokenHashingException(String message, Throwable cause) {
        super(ErrorCode.TOKEN_HASHING_FAILED, message, cause);
    }
}
