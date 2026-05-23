package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class EscrowTransactionAdapterTest {

  @Test
  @DisplayName("prod profile에서는 legacy escrow stub 기동을 막는다")
  void rejectUnsafeRuntime_rejectsProdProfile() {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    EscrowTransactionAdapter adapter = new EscrowTransactionAdapter(environment);

    assertThatThrownBy(adapter::rejectUnsafeRuntime)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("EscrowTransactionAdapter must not run");
  }

  @Test
  @DisplayName("marketplace admin enabled 환경에서는 legacy escrow stub 기동을 막는다")
  void rejectUnsafeRuntime_rejectsMarketplaceAdminEnabled() {
    MockEnvironment environment =
        new MockEnvironment().withProperty("web3.marketplace.admin.enabled", "true");
    EscrowTransactionAdapter adapter = new EscrowTransactionAdapter(environment);

    assertThatThrownBy(adapter::rejectUnsafeRuntime)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("marketplace admin");
  }

  @Test
  @DisplayName("non-prod 및 admin disabled 환경에서는 legacy stub을 허용한다")
  void rejectUnsafeRuntime_allowsSafeLegacyRuntime() {
    MockEnvironment environment =
        new MockEnvironment().withProperty("web3.marketplace.admin.enabled", "false");
    EscrowTransactionAdapter adapter = new EscrowTransactionAdapter(environment);

    assertThatNoException().isThrownBy(adapter::rejectUnsafeRuntime);
  }
}
