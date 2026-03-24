package momzzangseven.mztkbe.modules.image.application.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.image.application.service.ImagePendingCleanupService;
import momzzangseven.mztkbe.modules.image.infrastructure.scheduler.ImagePendingCleanupScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImagePendingCleanupScheduler 단위 테스트")
class ImagePendingCleanupSchedulerTest {

  @Mock private ImagePendingCleanupService cleanupService;

  @InjectMocks private ImagePendingCleanupScheduler scheduler;

  @Nested
  @DisplayName("[C-D-3] run() — while 루프 종료 조건")
  class WhileLoopTerminationTests {

    @Test
    @DisplayName("[C-H-2] 첫 번째 호출에서 0 반환 시 runBatch() 1회만 실행")
    void run_callsRunBatch1Time_whenReturns0Immediately() {
      given(cleanupService.runBatch(any())).willReturn(0);

      scheduler.run();

      verify(cleanupService, times(1)).runBatch(any());
    }

    @Test
    @DisplayName("[C-H-3] 100→50→0 순서로 반환될 때 runBatch() 3회 실행")
    void run_callsRunBatch3Times_whenReturns100_50_0() {
      given(cleanupService.runBatch(any())).willReturn(100).willReturn(50).willReturn(0);

      scheduler.run();

      verify(cleanupService, times(3)).runBatch(any());
    }

    @Test
    @DisplayName("[C-H-4] batchSize와 동일한 100 반환 후 0 반환 시 while 루프가 반드시 한 번 더 실행된다")
    void run_doesAnotherBatch_whenDeletedCountEqualsBatchSize() {
      given(cleanupService.runBatch(any())).willReturn(100).willReturn(0);

      scheduler.run();

      verify(cleanupService, times(2)).runBatch(any());
    }

    @Test
    @DisplayName("[C-E-7] batchSize=1 시나리오 — 서비스가 1,1,1,0 반환하면 4회 실행")
    void run_callsRunBatch4Times_whenBatchSize1With3Records() {
      given(cleanupService.runBatch(any())).willReturn(1).willReturn(1).willReturn(1).willReturn(0);

      scheduler.run();

      verify(cleanupService, times(4)).runBatch(any());
    }
  }

  @Nested
  @DisplayName("[C-D-4] run() — 누산 로직 (호출 횟수로 간접 검증)")
  class TotalDeletedTests {

    @Test
    @DisplayName("100→50→0 반환 시 서비스 3회 호출 — totalDeleted=150 누산 의도")
    void run_accumulatesTotalDeleted_150() {
      given(cleanupService.runBatch(any())).willReturn(100).willReturn(50).willReturn(0);

      scheduler.run();

      verify(cleanupService, times(3)).runBatch(any());
    }

    @Test
    @DisplayName("삭제 없음(0 반환) 시 서비스 1회만 호출 — totalDeleted=0 유지")
    void run_noAccumulation_whenNothingDeleted() {
      given(cleanupService.runBatch(any())).willReturn(0);

      scheduler.run();

      verify(cleanupService, times(1)).runBatch(any());
    }
  }
}
