package momzzangseven.mztkbe.modules.level.application.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.level.application.port.in.PurgeLevelDataUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LevelRetentionScheduler 단위 테스트")
class LevelRetentionSchedulerTest {

  @Mock private PurgeLevelDataUseCase purgeLevelDataUseCase;

  @InjectMocks private LevelRetentionScheduler scheduler;

  @Test
  @DisplayName("run() - purge 결과 0이면 로그 없음")
  void run_deletedIsZero_doesNotLog() {
    given(purgeLevelDataUseCase.execute(any())).willReturn(0);

    scheduler.run();

    verify(purgeLevelDataUseCase).execute(any());
  }

  @Test
  @DisplayName("run() - purge 결과 > 0이면 완료 로그")
  void run_deletedIsPositive_logs() {
    given(purgeLevelDataUseCase.execute(any())).willReturn(42);

    scheduler.run();

    verify(purgeLevelDataUseCase).execute(any());
  }
}
