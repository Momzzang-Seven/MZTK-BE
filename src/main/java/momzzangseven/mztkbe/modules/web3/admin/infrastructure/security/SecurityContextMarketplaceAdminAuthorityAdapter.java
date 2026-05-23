package momzzangseven.mztkbe.modules.web3.admin.infrastructure.security;

import java.util.Set;
import java.util.stream.Collectors;
import momzzangseven.mztkbe.global.config.ConditionalOnMarketplaceAdminEnabled;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminAuthorityView;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ResolveMarketplaceAdminAuthorityPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMarketplaceAdminEnabled
public class SecurityContextMarketplaceAdminAuthorityAdapter
    implements ResolveMarketplaceAdminAuthorityPort {

  private static final String ELEVATED_ADMIN_AUTHORITY = "ROLE_ADMIN_SEED";

  @Override
  public MarketplaceAdminAuthorityView resolve() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return resolve(Set.of());
    }
    return resolve(
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet()));
  }

  private MarketplaceAdminAuthorityView resolve(Set<String> reachableAuthorities) {
    if (reachableAuthorities.contains(ELEVATED_ADMIN_AUTHORITY)) {
      return MarketplaceAdminAuthorityView.elevatedAdmin();
    }
    return MarketplaceAdminAuthorityView.standardAdmin();
  }
}
