package momzzangseven.mztkbe.global.error.answer;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class AnswerNotFoundException extends BusinessException {

  public AnswerNotFoundException() {
    super(ErrorCode.ANSWER_NOT_FOUND);
  }
}
