package momzzangseven.mztkbe.modules.web3.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MarketplaceAdminAuthorityPolicy")
class MarketplaceAdminAuthorityPolicyTest {

  private final MarketplaceAdminAuthorityPolicy policy = new MarketplaceAdminAuthorityPolicy();

  @Test
  @DisplayName("ROLE_ADMIN 은 endpoint 접근 권한만 가지며 수동 override 권한은 없다")
  void resolve_standardAdmin() {
    var authority = policy.resolve(List.of("ROLE_ADMIN"));

    assertThat(authority.canEarlySettle()).isFalse();
    assertThat(authority.canManualRefund()).isFalse();
  }

  @Test
  @DisplayName("ROLE_ADMIN_GENERATED 도 수동 override 권한은 없다")
  void resolve_generatedAdmin() {
    var authority = policy.resolve(List.of("ROLE_ADMIN_GENERATED", "ROLE_ADMIN"));

    assertThat(authority.canEarlySettle()).isFalse();
    assertThat(authority.canManualRefund()).isFalse();
  }

  @Test
  @DisplayName("ROLE_ADMIN_SEED 는 early settle/manual refund override 권한을 가진다")
  void resolve_seedAdmin() {
    var authority = policy.resolve(List.of("ROLE_ADMIN_SEED", "ROLE_ADMIN"));

    assertThat(authority.canEarlySettle()).isTrue();
    assertThat(authority.canManualRefund()).isTrue();
  }

  @Test
  @DisplayName("인증 권한이 없으면 override 권한도 없다")
  void resolve_noAuthorities() {
    var authority = policy.resolve(null);

    assertThat(authority.canEarlySettle()).isFalse();
    assertThat(authority.canManualRefund()).isFalse();
  }
}
