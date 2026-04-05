package momzzangseven.mztkbe.modules.web3.execution.infrastructure.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.execution.application.service.ExecutionIntentCleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutionIntentCleanupSchedulerTest {

  @Mock private ExecutionIntentCleanupService cleanupService;

  @InjectMocks private ExecutionIntentCleanupScheduler scheduler;

  @Test
  void run_stopsImmediately_whenNothingDeleted() {
    when(cleanupService.runBatch(any()))
        .thenReturn(new ExecutionIntentCleanupService.CleanupBatchResult(0, 0, 0));

    scheduler.run();

    verify(cleanupService, times(1)).runBatch(any());
  }

  @Test
  void run_repeatsUntilBatchDeletesNothing() {
    when(cleanupService.runBatch(any()))
        .thenReturn(
            new ExecutionIntentCleanupService.CleanupBatchResult(2, 1, 1),
            new ExecutionIntentCleanupService.CleanupBatchResult(1, 0, 0),
            new ExecutionIntentCleanupService.CleanupBatchResult(0, 0, 0));

    scheduler.run();

    verify(cleanupService, times(3)).runBatch(any());
  }

  @Test
  void run_repeatsWhenOnlyUsageRowsAreDeleted() {
    when(cleanupService.runBatch(any()))
        .thenReturn(
            new ExecutionIntentCleanupService.CleanupBatchResult(0, 0, 2),
            new ExecutionIntentCleanupService.CleanupBatchResult(0, 0, 0));

    scheduler.run();

    verify(cleanupService, times(2)).runBatch(any());
  }
}
