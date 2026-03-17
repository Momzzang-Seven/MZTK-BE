package momzzangseven.mztkbe.global.error.answer;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class AnswerInvalidInputException extends BusinessException {

  public AnswerInvalidInputException(String message) {
    super(ErrorCode.INVALID_INPUT, message);
  }
}
