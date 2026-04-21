package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when a store address is null, blank, or exceeds the max length. */
public class MarketplaceInvalidStoreAddressException extends BusinessException {

  public MarketplaceInvalidStoreAddressException() {
    super(ErrorCode.MARKETPLACE_INVALID_STORE_ADDRESS);
  }

  public MarketplaceInvalidStoreAddressException(String message) {
    super(ErrorCode.MARKETPLACE_INVALID_STORE_ADDRESS, message);
  }
}
