package momzzangseven.mztkbe.global.error.location;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class InvalidGpsCoordinateException extends BusinessException {

  public InvalidGpsCoordinateException(String customMessage) {
    super(ErrorCode.COORDINATE_INVALID, customMessage);
  }
}
