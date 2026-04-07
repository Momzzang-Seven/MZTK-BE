package momzzangseven.mztkbe.modules.account.infrastructure.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import momzzangseven.mztkbe.modules.account.application.service.WithdrawalHardDeleteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WithdrawalHardDeleteScheduler 단위 테스트")
class WithdrawalHardDeleteSchedulerTest {

  @Mock private WithdrawalHardDeleteService hardDeleteService;

  @InjectMocks private WithdrawalHardDeleteScheduler scheduler;

  @Test
  @DisplayName("run() - 첫 배치에서 0 반환 시 루프 즉시 종료, 로그 없음")
  void run_noBatch_doesNotLog() {
    given(hardDeleteService.runBatch(any(Instant.class))).willReturn(0);

    scheduler.run();

    verify(hardDeleteService, times(1)).runBatch(any(Instant.class));
  }

  @Test
  @DisplayName("run() - 배치 삭제 후 0 반환 시 루프 종료, 완료 로그")
  void run_batchCompletedWithDeletions_logs() {
    given(hardDeleteService.runBatch(any(Instant.class))).willReturn(30).willReturn(0);

    scheduler.run();

    verify(hardDeleteService, times(2)).runBatch(any(Instant.class));
  }
}
