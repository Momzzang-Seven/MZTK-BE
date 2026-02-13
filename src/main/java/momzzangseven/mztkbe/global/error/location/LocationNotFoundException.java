package momzzangseven.mztkbe.global.error.location;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class LocationNotFoundException extends BusinessException {

  public LocationNotFoundException(String message) {
    super(ErrorCode.LOCATION_NOT_FOUND, message);
  }
}
