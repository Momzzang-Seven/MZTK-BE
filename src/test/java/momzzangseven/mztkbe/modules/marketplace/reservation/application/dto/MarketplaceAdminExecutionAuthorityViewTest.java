package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class MarketplaceAdminExecutionAuthorityViewTest {

  @Test
  void rejectsUnknownRelayerRegistrationStatus() {
    assertThatThrownBy(
            () ->
                new MarketplaceAdminExecutionAuthorityView(
                    false,
                    MarketplaceAdminExecutionAuthorityView.SERVER_RELAYER_ONLY,
                    true,
                    "0x1111111111111111111111111111111111111111",
                    false,
                    "MAYBE",
                    false,
                    false))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("relayerRegistrationStatus is invalid");
  }

  @Test
  void rejectsUncheckedRelayerRegistrationWhenServerSignerIsAvailable() {
    assertThatThrownBy(
            () ->
                new MarketplaceAdminExecutionAuthorityView(
                    false,
                    MarketplaceAdminExecutionAuthorityView.SERVER_RELAYER_ONLY,
                    true,
                    "0x1111111111111111111111111111111111111111",
                    false,
                    MarketplaceAdminExecutionAuthorityView.RELAYER_REGISTRATION_UNCHECKED,
                    false,
                    false))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("relayerRegistrationStatus must be checked");
  }
}
