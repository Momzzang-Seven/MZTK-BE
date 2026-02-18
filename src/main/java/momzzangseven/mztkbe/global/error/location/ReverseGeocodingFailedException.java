package momzzangseven.mztkbe.global.error.location;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class ReverseGeocodingFailedException extends BusinessException {
  public ReverseGeocodingFailedException(String message) {
    super(ErrorCode.REV_GEOCODING_FAILED, message);
  }
}
