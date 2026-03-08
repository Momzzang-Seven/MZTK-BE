package momzzangseven.mztkbe.modules.level.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;
import org.junit.jupiter.api.Test;

class DeleteUserLevelDataCommandTest {

  @Test
  void constructor_shouldThrowWhenUserIdsIsNull() {
    assertThatThrownBy(() -> new DeleteUserLevelDataCommand(null))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void constructor_shouldThrowWhenUserIdsIsEmpty() {
    assertThatThrownBy(() -> new DeleteUserLevelDataCommand(List.of()))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void constructor_shouldCreateWhenUserIdsPresent() {
    DeleteUserLevelDataCommand command = new DeleteUserLevelDataCommand(List.of(1L, 2L));

    assertThat(command.userIds()).containsExactly(1L, 2L);
  }
}
