package momzzangseven.mztkbe.modules.web3.qna.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.ScheduleNextQnaAutoAcceptResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ScheduleNextQnaAutoAcceptUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAutoAcceptPolicyPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QnaAutoAcceptBatchServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-17T01:00:00Z");

  @Mock private ScheduleNextQnaAutoAcceptUseCase scheduleNextQnaAutoAcceptUseCase;
  @Mock private LoadQnaAutoAcceptPolicyPort loadQnaAutoAcceptPolicyPort;

  private QnaAutoAcceptBatchService service;

  @BeforeEach
  void setUp() {
    service =
        new QnaAutoAcceptBatchService(
            scheduleNextQnaAutoAcceptUseCase, loadQnaAutoAcceptPolicyPort);
    when(loadQnaAutoAcceptPolicyPort.loadPolicy())
        .thenReturn(new LoadQnaAutoAcceptPolicyPort.QnaAutoAcceptPolicy(604_800L, 5));
  }

  @Test
  void runBatch_breaksAfterFirstSkippedCandidate() {
    when(scheduleNextQnaAutoAcceptUseCase.scheduleNext(NOW))
        .thenReturn(
            ScheduleNextQnaAutoAcceptResult.scheduled(),
            ScheduleNextQnaAutoAcceptResult.skipped(),
            ScheduleNextQnaAutoAcceptResult.exhausted());

    var result = service.runBatch(NOW);

    assertThat(result.scheduledCount()).isEqualTo(1);
    assertThat(result.skippedCount()).isEqualTo(1);
    assertThat(result.failedCount()).isZero();
  }

  @Test
  void runBatch_marksFailedAndStopsWhenSchedulingThrows() {
    when(scheduleNextQnaAutoAcceptUseCase.scheduleNext(NOW))
        .thenThrow(new IllegalStateException("boom"));

    var result = service.runBatch(NOW);

    assertThat(result.scheduledCount()).isZero();
    assertThat(result.skippedCount()).isZero();
    assertThat(result.failedCount()).isEqualTo(1);
  }
}
