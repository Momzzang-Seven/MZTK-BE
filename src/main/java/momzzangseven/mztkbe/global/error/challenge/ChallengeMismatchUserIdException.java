package momzzangseven.mztkbe.global.error.challenge;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class ChallengeMismatchUserIdException extends BusinessException {
  public ChallengeMismatchUserIdException() {
    super(ErrorCode.CHALLENGE_USERID_MISMATCH);
  }
}
