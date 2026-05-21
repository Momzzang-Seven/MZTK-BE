package momzzangseven.mztkbe.modules.web3.admin.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.admin.application.service.MarketplaceAdminAuthorityPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("SecurityContextMarketplaceAdminAuthorityAdapter")
class SecurityContextMarketplaceAdminAuthorityAdapterTest {

  private final SecurityContextMarketplaceAdminAuthorityAdapter adapter =
      new SecurityContextMarketplaceAdminAuthorityAdapter(new MarketplaceAdminAuthorityPolicy());

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("reachable ROLE_ADMIN_SEED 를 수동 marketplace override 권한으로 변환한다")
  void resolve_seedAdmin() {
    setAuthorities("ROLE_ADMIN_SEED", "ROLE_ADMIN");

    var authority = adapter.resolve();

    assertThat(authority.canEarlySettle()).isTrue();
    assertThat(authority.canManualRefund()).isTrue();
  }

  @Test
  @DisplayName("일반 ROLE_ADMIN 은 override 권한 없이 변환한다")
  void resolve_standardAdmin() {
    setAuthorities("ROLE_ADMIN");

    var authority = adapter.resolve();

    assertThat(authority.canEarlySettle()).isFalse();
    assertThat(authority.canManualRefund()).isFalse();
  }

  @Test
  @DisplayName("인증 컨텍스트가 없으면 override 권한 없이 변환한다")
  void resolve_noAuthentication() {
    var authority = adapter.resolve();

    assertThat(authority.canEarlySettle()).isFalse();
    assertThat(authority.canManualRefund()).isFalse();
  }

  private void setAuthorities(String... authorities) {
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(
            9L, null, List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    SecurityContextHolder.setContext(context);
  }
}
