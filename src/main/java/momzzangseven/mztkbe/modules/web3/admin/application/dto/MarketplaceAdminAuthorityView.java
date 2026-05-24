package momzzangseven.mztkbe.modules.web3.admin.application.dto;

public record MarketplaceAdminAuthorityView(boolean canEarlySettle, boolean canManualRefund) {

  public static MarketplaceAdminAuthorityView standardAdmin() {
    return new MarketplaceAdminAuthorityView(false, false);
  }

  public static MarketplaceAdminAuthorityView elevatedAdmin() {
    return new MarketplaceAdminAuthorityView(true, true);
  }
}
