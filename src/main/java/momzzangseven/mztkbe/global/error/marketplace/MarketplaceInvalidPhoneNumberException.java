package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Exception thrown when a phone number is blank or does not match the allowed format. */
public class MarketplaceInvalidPhoneNumberException extends BusinessException {

  public MarketplaceInvalidPhoneNumberException() {
    super(ErrorCode.MARKETPLACE_INVALID_PHONE_NUMBER);
  }

  public MarketplaceInvalidPhoneNumberException(String message) {
    super(ErrorCode.MARKETPLACE_INVALID_PHONE_NUMBER, message);
  }
}
