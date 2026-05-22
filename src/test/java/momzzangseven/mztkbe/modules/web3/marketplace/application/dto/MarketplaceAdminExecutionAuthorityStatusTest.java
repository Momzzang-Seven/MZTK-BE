package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class MarketplaceAdminExecutionAuthorityStatusTest {

  @Test
  void rejectsUnknownRelayerRegistrationStatus() {
    assertThatThrownBy(
            () ->
                new MarketplaceAdminExecutionAuthorityStatus(
                    false,
                    MarketplaceAdminExecutionAuthorityStatus.SERVER_RELAYER_ONLY,
                    true,
                    "0x1111111111111111111111111111111111111111",
                    false,
                    "MAYBE"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("relayerRegistrationStatus is invalid");
  }

  @Test
  void rejectsUncheckedRelayerRegistrationWhenServerSignerIsAvailable() {
    assertThatThrownBy(
            () ->
                new MarketplaceAdminExecutionAuthorityStatus(
                    false,
                    MarketplaceAdminExecutionAuthorityStatus.SERVER_RELAYER_ONLY,
                    true,
                    "0x1111111111111111111111111111111111111111",
                    false,
                    MarketplaceAdminExecutionAuthorityStatus.RELAYER_REGISTRATION_UNCHECKED))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("relayerRegistrationStatus must be checked");
  }
}
