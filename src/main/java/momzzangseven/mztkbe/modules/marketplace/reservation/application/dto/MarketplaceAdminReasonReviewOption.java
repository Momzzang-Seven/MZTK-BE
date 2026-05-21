package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.util.List;

public record MarketplaceAdminReasonReviewOption(
    String reasonCode,
    boolean processable,
    MarketplaceAdminReviewValidationCode blockingCode,
    boolean requiresConfirmation,
    String confirmationType,
    String requiredAuthority,
    boolean authoritySatisfied,
    String displayCode,
    MarketplaceAdminResultPreview resultPreview,
    List<MarketplaceAdminReviewValidationItem> validationItems) {

  public MarketplaceAdminReasonReviewOption {
    validationItems = validationItems == null ? List.of() : List.copyOf(validationItems);
  }
}
