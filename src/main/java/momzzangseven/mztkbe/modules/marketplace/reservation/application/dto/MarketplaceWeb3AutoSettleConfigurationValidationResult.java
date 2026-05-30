package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

public record MarketplaceWeb3AutoSettleConfigurationValidationResult(
    String authorityMisconfigurationMessage) {

  public MarketplaceWeb3AutoSettleConfigurationValidationResult {
    if (authorityMisconfigurationMessage != null && authorityMisconfigurationMessage.isBlank()) {
      throw new IllegalArgumentException(
          "authorityMisconfigurationMessage must be null or non-blank");
    }
  }

  public static MarketplaceWeb3AutoSettleConfigurationValidationResult ok() {
    return new MarketplaceWeb3AutoSettleConfigurationValidationResult(null);
  }

  public static MarketplaceWeb3AutoSettleConfigurationValidationResult authorityWarning(
      String message) {
    return new MarketplaceWeb3AutoSettleConfigurationValidationResult(message);
  }

  public boolean hasAuthorityWarning() {
    return authorityMisconfigurationMessage != null;
  }
}
