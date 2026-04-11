package momzzangseven.mztkbe.global.error.answer;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class CannotUpdateAnswerOnSolvedPostException extends BusinessException {

  public CannotUpdateAnswerOnSolvedPostException() {
    super(ErrorCode.CANNOT_UPDATE_ANSWER_ON_SOLVED_POST);
  }
}
