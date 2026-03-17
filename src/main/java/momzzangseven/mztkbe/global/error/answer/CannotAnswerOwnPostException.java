package momzzangseven.mztkbe.global.error.answer;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class CannotAnswerOwnPostException extends BusinessException {

  public CannotAnswerOwnPostException() {
    super(ErrorCode.CANNOT_ANSWER_OWN_POST);
  }
}
