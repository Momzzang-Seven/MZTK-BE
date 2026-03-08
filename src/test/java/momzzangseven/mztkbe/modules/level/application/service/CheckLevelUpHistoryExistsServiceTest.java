package momzzangseven.mztkbe.modules.level.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.level.application.port.out.LevelUpHistoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckLevelUpHistoryExistsServiceTest {

  @Mock private LevelUpHistoryPort levelUpHistoryPort;

  @InjectMocks private CheckLevelUpHistoryExistsService service;

  @Test
  void execute_shouldReturnPortResult() {
    when(levelUpHistoryPort.existsById(10L)).thenReturn(true);

    boolean exists = service.execute(10L);

    assertThat(exists).isTrue();
    verify(levelUpHistoryPort).existsById(10L);
  }
}
