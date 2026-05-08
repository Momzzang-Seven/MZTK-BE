package momzzangseven.mztkbe.modules.web3.qna.infrastructure.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.RunQnaQuestionUpdateReconciliationResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.RunQnaQuestionUpdateReconciliationUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QnaQuestionUpdateReconciliationSchedulerTest {

  @Mock private RunQnaQuestionUpdateReconciliationUseCase reconciliationUseCase;

  private QnaQuestionUpdateReconciliationScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new QnaQuestionUpdateReconciliationScheduler(reconciliationUseCase, 100);
  }

  @Test
  void run_loopsUntilBatchBecomesPartial() {
    when(reconciliationUseCase.run(any()))
        .thenReturn(
            new RunQnaQuestionUpdateReconciliationResult(100, 100, 0, 0),
            new RunQnaQuestionUpdateReconciliationResult(20, 20, 0, 0));

    scheduler.run();

    verify(reconciliationUseCase, times(2)).run(any());
  }

  @Test
  void run_stopsLoopWhenBatchReportsSkipped() {
    when(reconciliationUseCase.run(any()))
        .thenReturn(new RunQnaQuestionUpdateReconciliationResult(100, 0, 100, 0));

    scheduler.run();

    verify(reconciliationUseCase, times(1)).run(any());
  }

  @Test
  void run_stopsLoopWhenBatchReportsFailure() {
    when(reconciliationUseCase.run(any()))
        .thenReturn(new RunQnaQuestionUpdateReconciliationResult(100, 99, 0, 1));

    scheduler.run();

    verify(reconciliationUseCase, times(1)).run(any());
  }

  @Test
  void run_swallowsBatchFailure() {
    when(reconciliationUseCase.run(any())).thenThrow(new IllegalStateException("boom"));

    scheduler.run();

    verify(reconciliationUseCase, times(1)).run(any());
  }
}
