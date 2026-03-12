package momzzangseven.mztkbe.modules.user.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WithdrawUserCommand unit test")
class WithdrawUserCommandTest {

  @Test
  @DisplayName("of() creates command")
  void of_createsCommand() {
    WithdrawUserCommand command = WithdrawUserCommand.of(1L);

    assertThat(command.userId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("validate rejects null userId")
  void validate_nullUserId_throwsException() {
    WithdrawUserCommand command = new WithdrawUserCommand(null);

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("userId is required");
  }

  @Test
  @DisplayName("validate accepts positive userId")
  void validate_validUserId_doesNotThrow() {
    WithdrawUserCommand command = new WithdrawUserCommand(1L);

    assertThatCode(command::validate).doesNotThrowAnyException();
  }
}
