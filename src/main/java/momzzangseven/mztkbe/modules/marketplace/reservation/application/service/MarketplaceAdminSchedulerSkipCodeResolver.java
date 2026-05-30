package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import momzzangseven.mztkbe.global.error.marketplace.MarketplaceReservationStateException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationCode;

final class MarketplaceAdminSchedulerSkipCodeResolver {

  private MarketplaceAdminSchedulerSkipCodeResolver() {}

  static MarketplaceAdminReviewValidationCode resolve(
      MarketplaceReservationStateException exception) {
    MarketplaceAdminReviewValidationCode stableCode = parse(exception.stableCode());
    if (stableCode != null) {
      return stableCode;
    }
    return parse(exception.getMessage());
  }

  private static MarketplaceAdminReviewValidationCode parse(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return MarketplaceAdminReviewValidationCode.valueOf(value);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }
}
