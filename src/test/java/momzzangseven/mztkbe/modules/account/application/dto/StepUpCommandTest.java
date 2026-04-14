package momzzangseven.mztkbe.modules.account.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StepUpCommand unit test")
class StepUpCommandTest {

  @Test
  @DisplayName("of() creates command")
  void of_createsCommand() {
    StepUpCommand command = StepUpCommand.of(1L, "pw", null);

    assertThat(command.userId()).isEqualTo(1L);
    assertThat(command.password()).isEqualTo("pw");
    assertThat(command.authorizationCode()).isNull();
  }

  @Test
  @DisplayName("validate rejects null userId")
  void validate_nullUserId_throwsException() {
    StepUpCommand command = new StepUpCommand(null, null, null);

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("userId is required");
  }

  @Test
  @DisplayName("validate accepts positive userId")
  void validate_validUserId_doesNotThrow() {
    StepUpCommand command = new StepUpCommand(1L, null, "auth-code");

    assertThatCode(command::validate).doesNotThrowAnyException();
  }
}
