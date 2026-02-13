package momzzangseven.mztkbe.global.error.challenge;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class ChallengeNotFoundException extends BusinessException {
  public ChallengeNotFoundException() {
    super(ErrorCode.CHALLENGE_NOT_FOUND_OR_EXPIRED);
  }
}
