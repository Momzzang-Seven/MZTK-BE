package momzzangseven.mztkbe.modules.web3.qna.infrastructure.scheduler;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.RunQnaQuestionUpdateReconciliationResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.RunQnaQuestionUpdateReconciliationUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QnaQuestionUpdateReconciliationScheduler unit test")
class QnaQuestionUpdateReconciliationSchedulerTest {

  @Mock private RunQnaQuestionUpdateReconciliationUseCase reconciliationUseCase;

  @Test
  @DisplayName("run keeps reconciling full batches until a partial batch is reached")
  void runContinuesUntilPartialBatch() {
    QnaQuestionUpdateReconciliationScheduler scheduler =
        new QnaQuestionUpdateReconciliationScheduler(reconciliationUseCase, 100);
    when(reconciliationUseCase.run(argThat(command -> command.limit().equals(100))))
        .thenReturn(
            new RunQnaQuestionUpdateReconciliationResult(100, 100, 0, 0),
            new RunQnaQuestionUpdateReconciliationResult(20, 20, 0, 0));

    scheduler.run();

    verify(reconciliationUseCase, times(2)).run(argThat(command -> command.limit().equals(100)));
  }

  @Test
  @DisplayName("run stops after a skipped full batch to avoid rereading the same rows forever")
  void runStopsWhenFullBatchIsSkipped() {
    QnaQuestionUpdateReconciliationScheduler scheduler =
        new QnaQuestionUpdateReconciliationScheduler(reconciliationUseCase, 100);
    when(reconciliationUseCase.run(argThat(command -> command.limit().equals(100))))
        .thenReturn(new RunQnaQuestionUpdateReconciliationResult(100, 0, 100, 0));

    scheduler.run();

    verify(reconciliationUseCase).run(argThat(command -> command.limit().equals(100)));
  }

  @Test
  @DisplayName("run stops after a failed batch so the next schedule can retry")
  void runStopsWhenBatchHasFailure() {
    QnaQuestionUpdateReconciliationScheduler scheduler =
        new QnaQuestionUpdateReconciliationScheduler(reconciliationUseCase, 100);
    when(reconciliationUseCase.run(argThat(command -> command.limit().equals(100))))
        .thenReturn(new RunQnaQuestionUpdateReconciliationResult(100, 99, 0, 1));

    scheduler.run();

    verify(reconciliationUseCase).run(argThat(command -> command.limit().equals(100)));
  }
}
