package momzzangseven.mztkbe.modules.web3.challenge.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.modules.web3.challenge.domain.model.ChallengePurpose;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CreateChallengeCommand unit test")
class CreateChallengeCommandTest {

  @Test
  @DisplayName("constructor normalizes wallet address to lowercase")
  void constructor_normalizesWalletAddress() {
    CreateChallengeCommand command =
        new CreateChallengeCommand(
            1L, ChallengePurpose.WALLET_REGISTRATION, "0x52908400098527886E0F7030069857D2E4169EE7");

    assertThat(command.walletAddress()).isEqualTo("0x52908400098527886e0f7030069857d2e4169ee7");
  }

  @Test
  @DisplayName("validate rejects invalid userId")
  void validate_invalidUserId_throwsException() {
    CreateChallengeCommand command =
        new CreateChallengeCommand(0L, ChallengePurpose.WALLET_REGISTRATION, validAddress());

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("User ID must be positive");
  }

  @Test
  @DisplayName("validate rejects null purpose")
  void validate_nullPurpose_throwsException() {
    CreateChallengeCommand command = new CreateChallengeCommand(1L, null, validAddress());

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Purpose must not be null");
  }

  @Test
  @DisplayName("validate rejects invalid wallet format")
  void validate_invalidWalletFormat_throwsException() {
    CreateChallengeCommand command =
        new CreateChallengeCommand(1L, ChallengePurpose.WALLET_REGISTRATION, "abc");

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid Ethereum address format");
  }

  @Test
  @DisplayName("validate rejects blank wallet address")
  void validate_blankWallet_throwsException() {
    CreateChallengeCommand command =
        new CreateChallengeCommand(1L, ChallengePurpose.WALLET_REGISTRATION, " ");

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Wallet address must not be blank");
  }

  @Test
  @DisplayName("validate passes for valid command")
  void validate_validCommand_doesNotThrow() {
    CreateChallengeCommand command =
        new CreateChallengeCommand(1L, ChallengePurpose.WALLET_REGISTRATION, validAddress());

    assertThatCode(command::validate).doesNotThrowAnyException();
  }

  private String validAddress() {
    return "0x1111111111111111111111111111111111111111";
  }
}
