package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when a class title is null, blank, or exceeds the max length. */
public class MarketplaceInvalidTitleException extends BusinessException {

  public MarketplaceInvalidTitleException() {
    super(ErrorCode.MARKETPLACE_INVALID_TITLE);
  }

  public MarketplaceInvalidTitleException(String message) {
    super(ErrorCode.MARKETPLACE_INVALID_TITLE, message);
  }
}
