package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProvisionTreasuryKeyCommand unit test")
class ProvisionTreasuryKeyCommandTest {

  @Test
  @DisplayName("validate rejects non-positive operatorId")
  void validate_nonPositiveOperatorId_throwsException() {
    ProvisionTreasuryKeyCommand command = new ProvisionTreasuryKeyCommand(0L, "0xprivate", "alias");

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("operatorId must be positive");
  }

  @Test
  @DisplayName("validate rejects blank private key")
  void validate_blankPrivateKey_throwsException() {
    ProvisionTreasuryKeyCommand command = new ProvisionTreasuryKeyCommand(1L, " ", "alias");

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("treasuryPrivateKey is required");
  }

  @Test
  @DisplayName("validate rejects blank walletAlias when provided")
  void validate_blankWalletAlias_throwsException() {
    ProvisionTreasuryKeyCommand command = new ProvisionTreasuryKeyCommand(1L, "0xprivate", " ");

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("walletAlias must not be blank");
  }

  @Test
  @DisplayName("validate allows null walletAlias")
  void validate_nullWalletAlias_doesNotThrow() {
    ProvisionTreasuryKeyCommand command = new ProvisionTreasuryKeyCommand(1L, "0xprivate", null);

    assertThatCode(command::validate).doesNotThrowAnyException();
  }
}
