package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/**
 * Exception thrown when a store URL is malformed, exceeds max length, or uses a non-http scheme.
 */
public class MarketplaceInvalidStoreUrlException extends BusinessException {

  public MarketplaceInvalidStoreUrlException() {
    super(ErrorCode.MARKETPLACE_INVALID_STORE_URL);
  }

  public MarketplaceInvalidStoreUrlException(String message) {
    super(ErrorCode.MARKETPLACE_INVALID_STORE_URL, message);
  }
}
