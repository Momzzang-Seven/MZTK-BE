package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UnlinkWalletCommand unit test")
class UnlinkWalletCommandTest {

  @Test
  @DisplayName("constructor normalizes wallet address to lowercase")
  void constructor_normalizesWalletAddress() {
    UnlinkWalletCommand command =
        new UnlinkWalletCommand(1L, "0x52908400098527886E0F7030069857D2E4169EE7");

    assertThat(command.walletAddress()).isEqualTo("0x52908400098527886e0f7030069857d2e4169ee7");
  }

  @Test
  @DisplayName("validate rejects invalid userId")
  void validate_invalidUserId_throwsException() {
    UnlinkWalletCommand command = new UnlinkWalletCommand(0L, validAddress());

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("User ID must be positive");
  }

  @Test
  @DisplayName("validate rejects blank wallet address")
  void validate_blankWalletAddress_throwsException() {
    UnlinkWalletCommand command = new UnlinkWalletCommand(1L, " ");

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Wallet address must not be blank");
  }

  @Test
  @DisplayName("validate rejects invalid wallet format")
  void validate_invalidWalletFormat_throwsException() {
    UnlinkWalletCommand command = new UnlinkWalletCommand(1L, "abc");

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid Ethereum address format");
  }

  @Test
  @DisplayName("validate passes for valid command")
  void validate_validCommand_doesNotThrow() {
    UnlinkWalletCommand command = new UnlinkWalletCommand(1L, validAddress());

    assertThatCode(command::validate).doesNotThrowAnyException();
  }

  private String validAddress() {
    return "0x1111111111111111111111111111111111111111";
  }
}
