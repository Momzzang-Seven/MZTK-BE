package momzzangseven.mztkbe.global.error.location;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class MissingLocationInfoException extends BusinessException {
  public MissingLocationInfoException(String message) {
    super(ErrorCode.MISSING_LOCATION_FIELD, message);
  }
}
