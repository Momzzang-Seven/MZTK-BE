package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when a class ID is null or not a positive number. */
public class MarketplaceInvalidClassIdException extends BusinessException {

  public MarketplaceInvalidClassIdException() {
    super(ErrorCode.MARKETPLACE_INVALID_CLASS_ID);
  }
}
