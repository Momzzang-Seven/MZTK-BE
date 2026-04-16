package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when a class category is null. */
public class MarketplaceInvalidCategoryException extends BusinessException {

  public MarketplaceInvalidCategoryException() {
    super(ErrorCode.MARKETPLACE_INVALID_CATEGORY);
  }
}
