package momzzangseven.mztkbe.modules.level.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.util.List;
import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;
import momzzangseven.mztkbe.modules.level.application.dto.DeleteUserLevelDataCommand;
import momzzangseven.mztkbe.modules.level.application.port.out.LevelRetentionPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteUserLevelDataServiceTest {

  @Mock private LevelRetentionPort levelRetentionPort;

  @InjectMocks private DeleteUserLevelDataService service;

  @Test
  void execute_shouldThrowWhenCommandIsNull() {
    assertThatThrownBy(() -> service.execute(null))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void execute_shouldDeleteByUserIds() {
    DeleteUserLevelDataCommand command = new DeleteUserLevelDataCommand(List.of(1L, 2L));

    service.execute(command);

    verify(levelRetentionPort).deleteUserLevelDataByUserIds(List.of(1L, 2L));
  }
}
