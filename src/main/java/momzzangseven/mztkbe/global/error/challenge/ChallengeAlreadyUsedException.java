package momzzangseven.mztkbe.global.error.challenge;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class ChallengeAlreadyUsedException extends BusinessException {

  public ChallengeAlreadyUsedException() {
    super(ErrorCode.CHALLENGE_ALREADY_USED);
  }
}
