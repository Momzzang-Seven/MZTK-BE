package momzzangseven.mztkbe.global.error.answer;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class CannotDeleteAnswerOnSolvedPostException extends BusinessException {

  public CannotDeleteAnswerOnSolvedPostException() {
    super(ErrorCode.CANNOT_DELETE_ANSWER_ON_SOLVED_POST);
  }
}
