package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record MarketplaceAdminReviewValidationItem(
    MarketplaceAdminReviewValidationCode code,
    MarketplaceAdminReviewValidationSeverity severity,
    String message,
    boolean blocking) {

  public MarketplaceAdminReviewValidationItem {
    if (code == null || severity == null) {
      throw new Web3InvalidInputException("validation code and severity are required");
    }
    if (message == null || message.isBlank()) {
      throw new Web3InvalidInputException("validation message is required");
    }
    if (blocking != (severity == MarketplaceAdminReviewValidationSeverity.BLOCKING)) {
      throw new Web3InvalidInputException("blocking must match BLOCKING severity");
    }
  }

  public static MarketplaceAdminReviewValidationItem info(
      MarketplaceAdminReviewValidationCode code, String message) {
    return new MarketplaceAdminReviewValidationItem(
        code, MarketplaceAdminReviewValidationSeverity.INFO, message, false);
  }

  public static MarketplaceAdminReviewValidationItem blocking(
      MarketplaceAdminReviewValidationCode code, String message) {
    return new MarketplaceAdminReviewValidationItem(
        code, MarketplaceAdminReviewValidationSeverity.BLOCKING, message, true);
  }
}
