package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception for marketplace Web3 execution state conflicts. */
public class MarketplaceExecutionStateException extends BusinessException {

  public MarketplaceExecutionStateException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }
}
