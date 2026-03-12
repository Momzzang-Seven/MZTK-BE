package momzzangseven.mztkbe.global.error.answer;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class CannotDeleteAcceptedAnswerException extends BusinessException {

  public CannotDeleteAcceptedAnswerException() {
    super(ErrorCode.CANNOT_DELETE_ACCEPTED_ANSWER);
  }
}
