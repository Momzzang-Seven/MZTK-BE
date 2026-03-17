package momzzangseven.mztkbe.global.error.answer;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class CannotAnswerSolvedPostException extends BusinessException {

  public CannotAnswerSolvedPostException() {
    super(ErrorCode.CANNOT_ANSWER_SOLVED_POST);
  }
}
