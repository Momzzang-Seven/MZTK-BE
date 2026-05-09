package momzzangseven.mztkbe.global.error.answer;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class AnswerPublicationStateException extends BusinessException {

  public AnswerPublicationStateException(ErrorCode errorCode) {
    super(errorCode);
  }

  public AnswerPublicationStateException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }
}
