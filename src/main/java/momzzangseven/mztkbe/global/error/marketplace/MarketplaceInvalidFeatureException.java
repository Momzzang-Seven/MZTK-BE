package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when class features violate count or length constraints. */
public class MarketplaceInvalidFeatureException extends BusinessException {

  public MarketplaceInvalidFeatureException() {
    super(ErrorCode.MARKETPLACE_INVALID_FEATURE);
  }

  public MarketplaceInvalidFeatureException(String message) {
    super(ErrorCode.MARKETPLACE_INVALID_FEATURE, message);
  }
}
