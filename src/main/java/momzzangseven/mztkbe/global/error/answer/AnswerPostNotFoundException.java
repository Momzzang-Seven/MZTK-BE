package momzzangseven.mztkbe.global.error.answer;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class AnswerPostNotFoundException extends BusinessException {

  public AnswerPostNotFoundException() {
    super(ErrorCode.POST_NOT_FOUND);
  }
}
