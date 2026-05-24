package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceWeb3DisabledException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LegacyEscrowTransactionDisabledAdapterTest {

  private final LegacyEscrowTransactionDisabledAdapter adapter =
      new LegacyEscrowTransactionDisabledAdapter();

  @Test
  @DisplayName("legacy direct escrow 호출은 disabled 예외를 던진다")
  void legacyDirectEscrowSubmission_throwsDisabledException() {
    assertThatThrownBy(
            () -> adapter.submitPurchase("order", "delegation", "execution", BigInteger.ONE))
        .isInstanceOf(MarketplaceWeb3DisabledException.class);
    assertThatThrownBy(() -> adapter.submitCancel("order"))
        .isInstanceOf(MarketplaceWeb3DisabledException.class);
    assertThatThrownBy(() -> adapter.submitConfirm("order"))
        .isInstanceOf(MarketplaceWeb3DisabledException.class);
    assertThatThrownBy(() -> adapter.submitAdminRefund("order"))
        .isInstanceOf(MarketplaceWeb3DisabledException.class);
    assertThatThrownBy(() -> adapter.submitAdminSettle("order"))
        .isInstanceOf(MarketplaceWeb3DisabledException.class);
  }
}
