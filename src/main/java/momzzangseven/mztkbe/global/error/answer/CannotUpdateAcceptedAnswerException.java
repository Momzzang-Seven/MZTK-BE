package momzzangseven.mztkbe.global.error.answer;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class CannotUpdateAcceptedAnswerException extends BusinessException {

  public CannotUpdateAcceptedAnswerException() {
    super(ErrorCode.CANNOT_UPDATE_ACCEPTED_ANSWER);
  }
}
