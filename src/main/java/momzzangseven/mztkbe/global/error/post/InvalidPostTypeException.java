package momzzangseven.mztkbe.global.error.post;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class InvalidPostTypeException extends BusinessException {

  public InvalidPostTypeException() {
    super(ErrorCode.INVALID_POST_TYPE);
  }
}
