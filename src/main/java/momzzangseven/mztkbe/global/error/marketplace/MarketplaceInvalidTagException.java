package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when class tags violate count or length constraints. */
public class MarketplaceInvalidTagException extends BusinessException {

  public MarketplaceInvalidTagException() {
    super(ErrorCode.MARKETPLACE_INVALID_TAG);
  }

  public MarketplaceInvalidTagException(String message) {
    super(ErrorCode.MARKETPLACE_INVALID_TAG, message);
  }
}
