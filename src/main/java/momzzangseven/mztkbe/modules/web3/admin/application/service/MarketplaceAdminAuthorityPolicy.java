package momzzangseven.mztkbe.modules.web3.admin.application.service;

import java.util.Collection;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminAuthorityView;

public class MarketplaceAdminAuthorityPolicy {

  private static final String ELEVATED_ADMIN_AUTHORITY = "ROLE_ADMIN_SEED";

  public MarketplaceAdminAuthorityView resolve(Collection<String> reachableAuthorities) {
    if (reachableAuthorities != null && reachableAuthorities.contains(ELEVATED_ADMIN_AUTHORITY)) {
      return MarketplaceAdminAuthorityView.elevatedAdmin();
    }
    return MarketplaceAdminAuthorityView.standardAdmin();
  }
}
