package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception for reservation lifecycle, Web3 execution, or recovery state conflicts. */
public class MarketplaceReservationStateException extends BusinessException {

  public MarketplaceReservationStateException(ErrorCode errorCode) {
    super(errorCode);
  }

  public MarketplaceReservationStateException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }

  public MarketplaceReservationStateException(
      ErrorCode errorCode, String message, Throwable cause) {
    super(errorCode, message, cause);
  }

  public String stableCode() {
    return null;
  }
}
