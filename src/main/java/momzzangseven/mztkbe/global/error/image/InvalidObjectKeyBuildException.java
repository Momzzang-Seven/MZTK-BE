package momzzangseven.mztkbe.global.error.image;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class InvalidObjectKeyBuildException extends BusinessException {
  public InvalidObjectKeyBuildException(String msg) {
    super(ErrorCode.IMAGE_VIRTUAL_REF_TYPE_CANNOT_BUILD_OBJECT_KEY, msg);
  }
}
