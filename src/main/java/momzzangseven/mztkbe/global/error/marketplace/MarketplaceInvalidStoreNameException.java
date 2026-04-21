package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when a store name is null, blank, or exceeds the max length. */
public class MarketplaceInvalidStoreNameException extends BusinessException {

  public MarketplaceInvalidStoreNameException() {
    super(ErrorCode.MARKETPLACE_INVALID_STORE_NAME);
  }

  public MarketplaceInvalidStoreNameException(String message) {
    super(ErrorCode.MARKETPLACE_INVALID_STORE_NAME, message);
  }
}
