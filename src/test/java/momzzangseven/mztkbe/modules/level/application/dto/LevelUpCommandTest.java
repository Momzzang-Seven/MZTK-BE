package momzzangseven.mztkbe.modules.level.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;
import org.junit.jupiter.api.Test;

class LevelUpCommandTest {

  @Test
  void constructor_shouldThrowWhenUserIdInvalid() {
    assertThatThrownBy(() -> new LevelUpCommand(null))
        .isInstanceOf(LevelUpCommandInvalidException.class);
    assertThatThrownBy(() -> new LevelUpCommand(0L))
        .isInstanceOf(LevelUpCommandInvalidException.class);
    assertThatThrownBy(() -> new LevelUpCommand(-1L))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void of_shouldCreateCommandWhenUserIdPositive() {
    LevelUpCommand command = LevelUpCommand.of(1L);

    assertThat(command.userId()).isEqualTo(1L);
  }
}
