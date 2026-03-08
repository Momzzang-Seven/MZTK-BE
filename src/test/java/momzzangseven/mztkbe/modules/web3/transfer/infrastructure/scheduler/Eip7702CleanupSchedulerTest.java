package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.transfer.application.service.Eip7702CleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Eip7702CleanupSchedulerTest {

  @Mock private Eip7702CleanupService cleanupService;

  @InjectMocks private Eip7702CleanupScheduler scheduler;

  @Test
  void run_stopsImmediately_whenNothingDeleted() {
    when(cleanupService.runBatch(any()))
        .thenReturn(new Eip7702CleanupService.CleanupBatchResult(0, 0));

    scheduler.run();

    verify(cleanupService, times(1)).runBatch(any());
  }

  @Test
  void run_repeatsUntilBatchDeletesNothing() {
    when(cleanupService.runBatch(any()))
        .thenReturn(
            new Eip7702CleanupService.CleanupBatchResult(2, 1),
            new Eip7702CleanupService.CleanupBatchResult(1, 0),
            new Eip7702CleanupService.CleanupBatchResult(0, 0));

    scheduler.run();

    verify(cleanupService, times(3)).runBatch(any());
  }

  @Test
  void run_repeatsWhenOnlyUsageRowsAreDeleted() {
    when(cleanupService.runBatch(any()))
        .thenReturn(
            new Eip7702CleanupService.CleanupBatchResult(0, 2),
            new Eip7702CleanupService.CleanupBatchResult(0, 0));

    scheduler.run();

    verify(cleanupService, times(2)).runBatch(any());
  }
}
