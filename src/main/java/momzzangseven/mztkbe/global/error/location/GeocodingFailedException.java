package momzzangseven.mztkbe.global.error.location;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class GeocodingFailedException extends BusinessException {
  public GeocodingFailedException() {
    super(ErrorCode.GEOCODING_FAILED);
  }

  public GeocodingFailedException(String message) {
    super(ErrorCode.GEOCODING_FAILED, message);
  }
}
