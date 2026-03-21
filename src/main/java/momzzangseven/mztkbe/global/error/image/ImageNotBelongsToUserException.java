package momzzangseven.mztkbe.global.error.image;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class ImageNotBelongsToUserException extends BusinessException {
  public ImageNotBelongsToUserException(String message) {
    super(ErrorCode.IMAGE_ILLEGAL_OWNERSHIP, message);
  }
}
