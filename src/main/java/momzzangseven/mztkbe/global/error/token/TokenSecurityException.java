package momzzangseven.mztkbe.global.error.token;

import momzzangseven.mztkbe.global.error.ErrorCode;

public class TokenSecurityException extends TokenException {

    public TokenSecurityException() {
        super(ErrorCode.TOKEN_SECURITY_THREAT);
    }

    public TokenSecurityException(String message) {
        super(ErrorCode.TOKEN_SECURITY_THREAT, message);
    }
}
