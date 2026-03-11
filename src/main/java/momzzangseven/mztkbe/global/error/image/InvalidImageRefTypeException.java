package momzzangseven.mztkbe.global.error.image;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class InvalidImageRefTypeException extends BusinessException {

  public InvalidImageRefTypeException(String msg) {
    super(ErrorCode.IMAGE_REF_TYPE_INVALID, msg);
  }
}
