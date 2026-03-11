package momzzangseven.mztkbe.global.error.image;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class InvalidImageFileNameException extends BusinessException {

  public InvalidImageFileNameException(String msg) {
    super(ErrorCode.IMAGE_FILE_NAME_INVALID, msg);
  }
}
