package momzzangseven.mztkbe.modules.account.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WithdrawCommand unit test")
class WithdrawCommandTest {

  @Test
  @DisplayName("of() creates command")
  void of_createsCommand() {
    WithdrawCommand command = WithdrawCommand.of(1L);

    assertThat(command.userId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("validate rejects null userId")
  void validate_nullUserId_throwsException() {
    WithdrawCommand command = new WithdrawCommand(null);

    assertThatThrownBy(command::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("userId is required");
  }

  @Test
  @DisplayName("validate accepts positive userId")
  void validate_validUserId_doesNotThrow() {
    WithdrawCommand command = new WithdrawCommand(1L);

    assertThatCode(command::validate).doesNotThrowAnyException();
  }
}
