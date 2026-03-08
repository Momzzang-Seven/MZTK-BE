package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RegisterWalletCommand unit test")
class RegisterWalletCommandTest {

  @Test
  @DisplayName("constructor normalizes wallet address to lowercase")
  void constructor_normalizesWalletAddress() {
    RegisterWalletCommand command =
        new RegisterWalletCommand(
            1L, "0x52908400098527886E0F7030069857D2E4169EE7", validSignature(), "nonce-1");

    assertThat(command.walletAddress()).isEqualTo("0x52908400098527886e0f7030069857d2e4169ee7");
  }

  @Test
  @DisplayName("validate rejects invalid userId")
  void validate_invalidUserId_throwsException() {
    RegisterWalletCommand command =
        new RegisterWalletCommand(0L, validAddress(), validSignature(), "nonce-1");

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("User ID must be positive");
  }

  @Test
  @DisplayName("validate rejects invalid wallet format")
  void validate_invalidWallet_throwsException() {
    RegisterWalletCommand command =
        new RegisterWalletCommand(1L, "abc", validSignature(), "nonce-1");

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid Ethereum address format");
  }

  @Test
  @DisplayName("validate rejects blank wallet address")
  void validate_blankWallet_throwsException() {
    RegisterWalletCommand command = new RegisterWalletCommand(1L, " ", validSignature(), "nonce-1");

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Wallet address must not be blank");
  }

  @Test
  @DisplayName("validate rejects invalid signature format")
  void validate_invalidSignature_throwsException() {
    RegisterWalletCommand command =
        new RegisterWalletCommand(1L, validAddress(), "0x1234", "nonce-1");

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid signature format");
  }

  @Test
  @DisplayName("validate rejects blank signature")
  void validate_blankSignature_throwsException() {
    RegisterWalletCommand command = new RegisterWalletCommand(1L, validAddress(), " ", "nonce-1");

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Signature must not be blank");
  }

  @Test
  @DisplayName("validate rejects blank nonce")
  void validate_blankNonce_throwsException() {
    RegisterWalletCommand command =
        new RegisterWalletCommand(1L, validAddress(), validSignature(), " ");

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Nonce must not be blank");
  }

  @Test
  @DisplayName("validate passes for valid command")
  void validate_validCommand_doesNotThrow() {
    RegisterWalletCommand command =
        new RegisterWalletCommand(1L, validAddress(), validSignature(), "nonce-1");

    assertThatCode(command::validate).doesNotThrowAnyException();
  }

  private String validAddress() {
    return "0x1111111111111111111111111111111111111111";
  }

  private String validSignature() {
    return "0x" + "a".repeat(130);
  }
}
