package momzzangseven.mztkbe.global.error.answer;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class AnswerUnauthorizedException extends BusinessException {

  public AnswerUnauthorizedException() {
    super(ErrorCode.ANSWER_UNAUTHORIZED);
  }
}
