package momzzangseven.mztkbe.global.error.post;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class PostUnauthorizedException extends BusinessException {

  public PostUnauthorizedException() {
    super(ErrorCode.POST_UNAUTHORIZED);
  }
}
