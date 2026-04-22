package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when a class capacity is invalid (e.g., less than 1). */
public class MarketplaceInvalidCapacityException extends BusinessException {

  public MarketplaceInvalidCapacityException() {
    super(ErrorCode.MARKETPLACE_INVALID_CAPACITY);
  }

  public MarketplaceInvalidCapacityException(String message) {
    super(ErrorCode.MARKETPLACE_INVALID_CAPACITY, message);
  }
}
