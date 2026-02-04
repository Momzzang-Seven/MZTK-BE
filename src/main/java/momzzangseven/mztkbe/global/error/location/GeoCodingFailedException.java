package momzzangseven.mztkbe.global.error.location;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class GeoCodingFailedException extends BusinessException {
  public GeoCodingFailedException() {
    super(ErrorCode.GEOCODING_FAILED);
  }

  public GeoCodingFailedException(String message) {
    super(ErrorCode.GEOCODING_FAILED, message);
  }
}
