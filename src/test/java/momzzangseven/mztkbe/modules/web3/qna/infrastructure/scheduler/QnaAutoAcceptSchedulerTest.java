package momzzangseven.mztkbe.modules.web3.qna.infrastructure.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.RunQnaAutoAcceptBatchResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.RunQnaAutoAcceptBatchUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QnaAutoAcceptSchedulerTest {

  @Mock private RunQnaAutoAcceptBatchUseCase runQnaAutoAcceptBatchUseCase;

  @InjectMocks private QnaAutoAcceptScheduler scheduler;

  @Test
  void run_loopsUntilBatchBecomesEmpty() {
    when(runQnaAutoAcceptBatchUseCase.runBatch(any()))
        .thenReturn(new RunQnaAutoAcceptBatchResult(1, 0, 0))
        .thenReturn(new RunQnaAutoAcceptBatchResult(0, 0, 0));

    scheduler.run();

    verify(runQnaAutoAcceptBatchUseCase, times(2)).runBatch(any());
  }

  @Test
  void run_stopsLoopWhenBatchReportsFailure() {
    when(runQnaAutoAcceptBatchUseCase.runBatch(any()))
        .thenReturn(new RunQnaAutoAcceptBatchResult(0, 0, 1));

    scheduler.run();

    verify(runQnaAutoAcceptBatchUseCase, times(1)).runBatch(any());
  }

  @Test
  void run_stopsLoopWhenBatchReportsSkipped() {
    when(runQnaAutoAcceptBatchUseCase.runBatch(any()))
        .thenReturn(new RunQnaAutoAcceptBatchResult(0, 1, 0));

    scheduler.run();

    verify(runQnaAutoAcceptBatchUseCase, times(1)).runBatch(any());
  }

  @Test
  void run_swallowsBatchFailure() {
    when(runQnaAutoAcceptBatchUseCase.runBatch(any())).thenThrow(new IllegalStateException("boom"));

    scheduler.run();

    verify(runQnaAutoAcceptBatchUseCase, times(1)).runBatch(any());
  }
}
