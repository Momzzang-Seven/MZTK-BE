package momzzangseven.mztkbe.global.error.post;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class PostNotFoundException extends BusinessException {
  public PostNotFoundException() {
    super(ErrorCode.POST_NOT_FOUND);
  }
}
