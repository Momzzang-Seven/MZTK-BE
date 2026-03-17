package momzzangseven.mztkbe.global.error.image;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class ImageNotFoundException extends BusinessException {
  public ImageNotFoundException(String objectKey) {
    super(ErrorCode.IMAGE_NOT_FOUND, "Image not found: " + objectKey);
  }
}
