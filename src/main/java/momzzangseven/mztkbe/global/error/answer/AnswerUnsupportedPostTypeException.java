package momzzangseven.mztkbe.global.error.answer;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class AnswerUnsupportedPostTypeException extends BusinessException {

  public AnswerUnsupportedPostTypeException() {
    super(ErrorCode.INVALID_POST_INPUT, "Cannot write an answer on a non-question post");
  }
}
