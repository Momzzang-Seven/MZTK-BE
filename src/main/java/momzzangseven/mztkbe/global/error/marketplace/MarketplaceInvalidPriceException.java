package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when a class price is invalid (e.g., not positive). */
public class MarketplaceInvalidPriceException extends BusinessException {

  public MarketplaceInvalidPriceException() {
    super(ErrorCode.MARKETPLACE_INVALID_PRICE);
  }

  public MarketplaceInvalidPriceException(String message) {
    super(ErrorCode.MARKETPLACE_INVALID_PRICE, message);
  }
}
