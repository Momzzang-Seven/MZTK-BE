package momzzangseven.mztkbe.global.error.user;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class IllegalTrainerGrantException extends BusinessException {

    public IllegalTrainerGrantException(String message) {
        super(ErrorCode.ILLEGAL_TRAINER_GRANT, ErrorCode.ILLEGAL_TRAINER_GRANT.getMessage() + ": " + message);
    }
}
