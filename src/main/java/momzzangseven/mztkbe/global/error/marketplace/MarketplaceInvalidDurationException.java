package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when class slots have inconsistent durations. */
public class MarketplaceInvalidDurationException extends BusinessException {

  public MarketplaceInvalidDurationException() {
    super(ErrorCode.MARKETPLACE_INVALID_DURATION);
  }

  public MarketplaceInvalidDurationException(String message) {
    super(ErrorCode.MARKETPLACE_INVALID_DURATION, message);
  }
}
