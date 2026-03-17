package momzzangseven.mztkbe.global.error.image;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class ImageStatusInvalidException extends BusinessException {
  public ImageStatusInvalidException(String message) {
    super(ErrorCode.IMAGE_STATUS_INVALID, message);
  }
}
