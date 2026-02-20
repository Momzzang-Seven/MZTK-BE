package momzzangseven.mztkbe.global.error.location;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class LocationAlreadyDeletedException extends BusinessException {
  public LocationAlreadyDeletedException(String message) {
    super(ErrorCode.LOCATION_ALREADY_DELETED, message);
  }
}
