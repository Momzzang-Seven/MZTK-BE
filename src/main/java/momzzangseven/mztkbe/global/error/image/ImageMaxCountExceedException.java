package momzzangseven.mztkbe.global.error.image;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class ImageMaxCountExceedException extends BusinessException {

  public ImageMaxCountExceedException(String msg) {
    super(ErrorCode.IMAGE_COUNT_EXCEEDED, msg);
  }
}
