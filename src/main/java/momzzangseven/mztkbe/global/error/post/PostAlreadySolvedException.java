package momzzangseven.mztkbe.global.error.post;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class PostAlreadySolvedException extends BusinessException {

  public PostAlreadySolvedException() {
    super(ErrorCode.POST_ALREADY_SOLVED);
  }
}
