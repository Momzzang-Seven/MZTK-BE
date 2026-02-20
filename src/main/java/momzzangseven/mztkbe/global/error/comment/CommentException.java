package momzzangseven.mztkbe.global.error.comment;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class CommentException extends BusinessException {
  public CommentException(ErrorCode errorCode) {
    super(errorCode);
  }
}
