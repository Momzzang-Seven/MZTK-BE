package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when a class description is null or blank. */
public class MarketplaceInvalidDescriptionException extends BusinessException {

  public MarketplaceInvalidDescriptionException() {
    super(ErrorCode.MARKETPLACE_INVALID_DESCRIPTION);
  }

  public MarketplaceInvalidDescriptionException(String message) {
    super(ErrorCode.MARKETPLACE_INVALID_DESCRIPTION, message);
  }
}
