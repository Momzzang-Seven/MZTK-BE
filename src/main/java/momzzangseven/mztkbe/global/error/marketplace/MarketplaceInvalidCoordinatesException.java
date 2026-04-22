package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when latitude or longitude is out of the valid geographic range. */
public class MarketplaceInvalidCoordinatesException extends BusinessException {

  public MarketplaceInvalidCoordinatesException() {
    super(ErrorCode.MARKETPLACE_INVALID_COORDINATES);
  }

  public MarketplaceInvalidCoordinatesException(String message) {
    super(ErrorCode.MARKETPLACE_INVALID_COORDINATES, message);
  }
}
