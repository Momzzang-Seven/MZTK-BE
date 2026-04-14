package momzzangseven.mztkbe.global.error.answer;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class AnswerPostMismatchException extends BusinessException {

  public AnswerPostMismatchException() {
    super(ErrorCode.ANSWER_POST_MISMATCH);
  }
}
