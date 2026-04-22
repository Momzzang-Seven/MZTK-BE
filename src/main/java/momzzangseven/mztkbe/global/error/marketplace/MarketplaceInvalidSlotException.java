package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when a class slot has invalid days or start time. */
public class MarketplaceInvalidSlotException extends BusinessException {

  public MarketplaceInvalidSlotException(String message) {
    super(ErrorCode.MARKETPLACE_INVALID_SLOT, message);
  }
}
