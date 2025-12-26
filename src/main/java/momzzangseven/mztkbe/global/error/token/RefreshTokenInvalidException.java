package momzzangseven.mztkbe.global.error.token;

import momzzangseven.mztkbe.global.error.ErrorCode;

public class RefreshTokenInvalidException extends TokenException {

    public RefreshTokenInvalidException(String detail) {
        super(ErrorCode.REFRESH_TOKEN_INVALID, ErrorCode.REFRESH_TOKEN_INVALID.getMessage() + ": " + detail);
    }
}
