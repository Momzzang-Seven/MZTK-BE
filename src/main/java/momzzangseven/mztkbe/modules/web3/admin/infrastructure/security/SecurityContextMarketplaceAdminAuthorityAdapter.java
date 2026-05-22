package momzzangseven.mztkbe.modules.web3.admin.infrastructure.security;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.config.ConditionalOnMarketplaceAdminEnabled;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminAuthorityView;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ResolveMarketplaceAdminAuthorityPort;
import momzzangseven.mztkbe.modules.web3.admin.application.service.MarketplaceAdminAuthorityPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnMarketplaceAdminEnabled
public class SecurityContextMarketplaceAdminAuthorityAdapter
    implements ResolveMarketplaceAdminAuthorityPort {

  private final MarketplaceAdminAuthorityPolicy policy;

  @Override
  public MarketplaceAdminAuthorityView resolve() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return policy.resolve(Set.of());
    }
    return policy.resolve(
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet()));
  }
}
