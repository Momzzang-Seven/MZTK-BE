package momzzangseven.mztkbe.modules.account.infrastructure.adapter;

import static org.mockito.Mockito.verify;

import java.util.List;
import momzzangseven.mztkbe.modules.level.application.dto.DeleteUserLevelDataCommand;
import momzzangseven.mztkbe.modules.level.application.port.in.DeleteUserLevelDataUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LevelAdapter unit test")
class LevelAdapterTest {

  @Mock private DeleteUserLevelDataUseCase deleteUserLevelDataUseCase;

  @InjectMocks private LevelAdapter adapter;

  @Test
  @DisplayName("[M-150] deleteByUserIds delegates to DeleteUserLevelDataUseCase")
  void deleteByUserIds_delegatesToUseCase() {
    List<Long> userIds = List.of(1L, 2L, 3L);

    adapter.deleteByUserIds(userIds);

    ArgumentCaptor<DeleteUserLevelDataCommand> captor =
        ArgumentCaptor.forClass(DeleteUserLevelDataCommand.class);
    verify(deleteUserLevelDataUseCase).execute(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().userIds())
        .containsExactly(1L, 2L, 3L);
  }
}
