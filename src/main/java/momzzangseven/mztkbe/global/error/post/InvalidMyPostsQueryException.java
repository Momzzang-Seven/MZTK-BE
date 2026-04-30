package momzzangseven.mztkbe.global.error.post;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class InvalidMyPostsQueryException extends BusinessException {

  public InvalidMyPostsQueryException(String message) {
    super(ErrorCode.INVALID_INPUT, message);
  }
}
