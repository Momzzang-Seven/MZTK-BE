package momzzangseven.mztkbe.modules.web3.qna.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAdminSettleCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.ScheduleNextQnaAutoAcceptResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.QuestionEscrowExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.ClaimNextQnaAutoAcceptCandidatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAcceptContextPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAutoAcceptPolicyPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAcceptStateSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAutoAcceptCandidate;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaExecutionIntentStateView;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleNextQnaAutoAcceptServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-17T01:00:00Z");

  @Mock private ClaimNextQnaAutoAcceptCandidatePort claimNextQnaAutoAcceptCandidatePort;
  @Mock private LoadQnaAutoAcceptPolicyPort loadQnaAutoAcceptPolicyPort;
  @Mock private LoadQnaAcceptContextPort loadQnaAcceptContextPort;
  @Mock private LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort;
  @Mock private QnaAcceptStateSyncPort qnaAcceptStateSyncPort;
  @Mock private QuestionEscrowExecutionUseCase questionEscrowExecutionUseCase;

  private ScheduleNextQnaAutoAcceptService service;

  @BeforeEach
  void setUp() {
    service =
        new ScheduleNextQnaAutoAcceptService(
            claimNextQnaAutoAcceptCandidatePort,
            loadQnaAutoAcceptPolicyPort,
            loadQnaAcceptContextPort,
            loadQnaExecutionIntentStatePort,
            qnaAcceptStateSyncPort,
            questionEscrowExecutionUseCase,
            Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
    when(loadQnaAutoAcceptPolicyPort.loadPolicy())
        .thenReturn(new LoadQnaAutoAcceptPolicyPort.QnaAutoAcceptPolicy(604_800L, 50));
  }

  @Test
  void scheduleNext_returnsFalseWhenNoCandidateExists() {
    when(claimNextQnaAutoAcceptCandidatePort.claimNextCandidate(any()))
        .thenReturn(Optional.empty());

    ScheduleNextQnaAutoAcceptResult result = service.scheduleNext(NOW);

    assertThat(result.isExhausted()).isTrue();
    verify(qnaAcceptStateSyncPort, never())
        .beginPendingAccept(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void scheduleNext_startsPendingAcceptAndPreparesAdminSettle() {
    when(claimNextQnaAutoAcceptCandidatePort.claimNextCandidate(any()))
        .thenReturn(
            Optional.of(
                new QnaAutoAcceptCandidate(
                    101L, 201L, 7L, 22L, java.time.LocalDateTime.of(2026, 4, 10, 10, 0))));
    when(loadQnaExecutionIntentStatePort.loadLatestActiveByResource(
            QnaExecutionResourceType.QUESTION, "101"))
        .thenReturn(Optional.empty());
    when(loadQnaExecutionIntentStatePort.loadLatestActiveByResource(
            QnaExecutionResourceType.ANSWER, "201"))
        .thenReturn(Optional.empty());
    when(loadQnaAcceptContextPort.loadForUpdate(101L, 201L))
        .thenReturn(
            Optional.of(
                new LoadQnaAcceptContextPort.QnaAcceptContext(101L, 7L, 201L, 22L, "질문", "답변")));

    ScheduleNextQnaAutoAcceptResult result = service.scheduleNext(NOW);

    assertThat(result.isScheduled()).isTrue();
    verify(qnaAcceptStateSyncPort).beginPendingAccept(101L, 201L);
    verify(questionEscrowExecutionUseCase)
        .prepareAdminSettle(new PrepareAdminSettleCommand(101L, 201L, 7L, 22L, "질문", "답변"));
  }

  @Test
  void scheduleNext_returnsFalseWhenActiveIntentExists() {
    when(claimNextQnaAutoAcceptCandidatePort.claimNextCandidate(any()))
        .thenReturn(
            Optional.of(
                new QnaAutoAcceptCandidate(
                    101L, 201L, 7L, 22L, java.time.LocalDateTime.of(2026, 4, 10, 10, 0))));
    when(loadQnaExecutionIntentStatePort.loadLatestActiveByResource(
            QnaExecutionResourceType.QUESTION, "101"))
        .thenReturn(
            Optional.of(
                new QnaExecutionIntentStateView(
                    "intent-1",
                    QnaExecutionActionType.QNA_ADMIN_SETTLE,
                    ExecutionIntentStatus.AWAITING_SIGNATURE)));

    ScheduleNextQnaAutoAcceptResult result = service.scheduleNext(NOW);

    assertThat(result.isSkipped()).isTrue();
    verify(qnaAcceptStateSyncPort, never()).beginPendingAccept(101L, 201L);
    verify(questionEscrowExecutionUseCase, never())
        .prepareAdminSettle(org.mockito.ArgumentMatchers.any());
  }
}
