package momzzangseven.mztkbe.modules.answer.application.scheduler;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.answer.application.port.in.RunOrphanAnswerCleanupBatchUseCase;
import momzzangseven.mztkbe.modules.answer.infrastructure.scheduler.AnswerOrphanCleanupScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerOrphanCleanupScheduler 단위 테스트")
class AnswerOrphanCleanupSchedulerTest {

  @Mock private RunOrphanAnswerCleanupBatchUseCase cleanupUseCase;

  @InjectMocks private AnswerOrphanCleanupScheduler scheduler;

  @Test
  @DisplayName("첫 배치에서 0을 반환하면 한 번만 실행한다")
  void run_callsRunBatchOnce_whenNoWork() {
    given(cleanupUseCase.runBatch()).willReturn(0);

    scheduler.run();

    verify(cleanupUseCase, times(1)).runBatch();
  }

  @Test
  @DisplayName("2,1,0 순서로 반환되면 세 번 실행한다")
  void run_repeatsUntilZero() {
    given(cleanupUseCase.runBatch()).willReturn(2).willReturn(1).willReturn(0);

    scheduler.run();

    verify(cleanupUseCase, times(3)).runBatch();
  }
}
