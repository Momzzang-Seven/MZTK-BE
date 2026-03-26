package momzzangseven.mztkbe.global.error.post;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class AnswerNotBelongToPostException extends BusinessException {

  public AnswerNotBelongToPostException() {
    super(ErrorCode.ANSWER_NOT_BELONG_TO_POST);
  }
}
