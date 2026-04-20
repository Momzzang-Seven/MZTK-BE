package momzzangseven.mztkbe.modules.web3.execution.infrastructure.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.RunInternalExecutionBatchResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.RunInternalExecutionBatchUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalExecutionIntentSchedulerTest {

  @Mock private RunInternalExecutionBatchUseCase runInternalExecutionBatchUseCase;

  @InjectMocks private InternalExecutionIntentScheduler scheduler;

  @Test
  void run_loopsUntilBatchBecomesEmpty() {
    when(runInternalExecutionBatchUseCase.runBatch(any()))
        .thenReturn(new RunInternalExecutionBatchResult(1, 1, 0, 0, 0))
        .thenReturn(new RunInternalExecutionBatchResult(1, 0, 1, 0, 0))
        .thenReturn(new RunInternalExecutionBatchResult(0, 0, 0, 0, 0));

    scheduler.run();

    verify(runInternalExecutionBatchUseCase, times(3)).runBatch(any());
  }

  @Test
  void run_stopsLoopWhenBatchReportsFailure() {
    when(runInternalExecutionBatchUseCase.runBatch(any()))
        .thenReturn(new RunInternalExecutionBatchResult(0, 0, 0, 0, 1));

    scheduler.run();

    verify(runInternalExecutionBatchUseCase, times(1)).runBatch(any());
  }

  @Test
  void run_continuesAfterQuarantinedBatchUntilEmpty() {
    when(runInternalExecutionBatchUseCase.runBatch(any()))
        .thenReturn(new RunInternalExecutionBatchResult(1, 0, 0, 1, 0))
        .thenReturn(new RunInternalExecutionBatchResult(0, 0, 0, 0, 0));

    scheduler.run();

    verify(runInternalExecutionBatchUseCase, times(2)).runBatch(any());
  }

  @Test
  void run_swallowsBatchFailure() {
    when(runInternalExecutionBatchUseCase.runBatch(any()))
        .thenThrow(new IllegalStateException("boom"));

    scheduler.run();

    verify(runInternalExecutionBatchUseCase, times(1)).runBatch(any());
  }
}
