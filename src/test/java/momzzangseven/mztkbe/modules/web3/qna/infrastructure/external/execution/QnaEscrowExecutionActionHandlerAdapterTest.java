package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAcceptStateSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaAnswerProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QnaEscrowExecutionActionHandlerAdapter unit test")
class QnaEscrowExecutionActionHandlerAdapterTest {

  @Mock private QnaProjectionPersistencePort qnaProjectionPersistencePort;
  @Mock private QnaAcceptStateSyncPort qnaAcceptStateSyncPort;

  private QnaEscrowExecutionActionHandlerAdapter adapter;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    adapter =
        new QnaEscrowExecutionActionHandlerAdapter(
            objectMapper, qnaProjectionPersistencePort, qnaAcceptStateSyncPort);
  }

  @Test
  @DisplayName("afterExecutionConfirmed creates question projection for confirmed create")
  void afterExecutionConfirmed_createsQuestionProjection() throws Exception {
    QnaEscrowExecutionPayload payload =
        new QnaEscrowExecutionPayload(
            QnaExecutionActionType.QNA_QUESTION_CREATE,
            101L,
            null,
            "0x" + "1".repeat(40),
            "0x" + "2".repeat(40),
            new BigInteger("50000000000000000000"),
            "0x" + "a".repeat(64),
            null,
            "0x" + "3".repeat(40),
            "0x1234");

    adapter.afterExecutionConfirmed(
        intent(payload, ExecutionResourceType.QUESTION, "101", 7L), plan());

    ArgumentCaptor<QnaQuestionProjection> captor =
        ArgumentCaptor.forClass(QnaQuestionProjection.class);
    verify(qnaProjectionPersistencePort).saveQuestion(captor.capture());
    assertThat(captor.getValue().getPostId()).isEqualTo(101L);
    assertThat(captor.getValue().getAskerUserId()).isEqualTo(7L);
    assertThat(captor.getValue().getQuestionHash()).isEqualTo("0x" + "a".repeat(64));
    assertThat(captor.getValue().getState()).isEqualTo(QnaQuestionState.CREATED);
  }

  @Test
  @DisplayName("afterExecutionConfirmed increments answer count and saves answer on submit")
  void afterExecutionConfirmed_submitsAnswerProjection() throws Exception {
    when(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .thenReturn(Optional.of(questionProjection("0x" + "a".repeat(64), 0)));
    QnaEscrowExecutionPayload payload =
        new QnaEscrowExecutionPayload(
            QnaExecutionActionType.QNA_ANSWER_SUBMIT,
            101L,
            201L,
            "0x" + "1".repeat(40),
            "0x" + "2".repeat(40),
            BigInteger.ZERO,
            "0x" + "a".repeat(64),
            "0x" + "b".repeat(64),
            "0x" + "3".repeat(40),
            "0x1234");

    adapter.afterExecutionConfirmed(
        intent(payload, ExecutionResourceType.ANSWER, "201", 22L), plan());

    ArgumentCaptor<QnaQuestionProjection> questionCaptor =
        ArgumentCaptor.forClass(QnaQuestionProjection.class);
    verify(qnaProjectionPersistencePort).saveQuestion(questionCaptor.capture());
    assertThat(questionCaptor.getValue().getAnswerCount()).isEqualTo(1);
    assertThat(questionCaptor.getValue().getState()).isEqualTo(QnaQuestionState.ANSWERED);

    ArgumentCaptor<QnaAnswerProjection> answerCaptor =
        ArgumentCaptor.forClass(QnaAnswerProjection.class);
    verify(qnaProjectionPersistencePort).saveAnswer(answerCaptor.capture());
    assertThat(answerCaptor.getValue().getAnswerId()).isEqualTo(201L);
    assertThat(answerCaptor.getValue().getContentHash()).isEqualTo("0x" + "b".repeat(64));
  }

  @Test
  @DisplayName("afterExecutionConfirmed decrements answer count and deletes answer on delete")
  void afterExecutionConfirmed_deletesAnswerProjection() throws Exception {
    when(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .thenReturn(Optional.of(questionProjection("0x" + "a".repeat(64), 1)));
    when(qnaProjectionPersistencePort.findAnswerByAnswerIdForUpdate(201L))
        .thenReturn(Optional.of(answerProjection("0x" + "b".repeat(64))));
    QnaEscrowExecutionPayload payload =
        new QnaEscrowExecutionPayload(
            QnaExecutionActionType.QNA_ANSWER_DELETE,
            101L,
            201L,
            "0x" + "1".repeat(40),
            "0x" + "2".repeat(40),
            BigInteger.ZERO,
            "0x" + "a".repeat(64),
            null,
            "0x" + "3".repeat(40),
            "0x1234");

    adapter.afterExecutionConfirmed(
        intent(payload, ExecutionResourceType.ANSWER, "201", 22L), plan());

    verify(qnaProjectionPersistencePort).deleteAnswerByAnswerId(201L);
    ArgumentCaptor<QnaQuestionProjection> questionCaptor =
        ArgumentCaptor.forClass(QnaQuestionProjection.class);
    verify(qnaProjectionPersistencePort).saveQuestion(questionCaptor.capture());
    assertThat(questionCaptor.getValue().getAnswerCount()).isEqualTo(0);
    assertThat(questionCaptor.getValue().getState()).isEqualTo(QnaQuestionState.CREATED);
  }

  @Test
  @DisplayName("afterExecutionConfirmed marks paid out using stored answer key on accept")
  void afterExecutionConfirmed_marksAccepted() throws Exception {
    when(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .thenReturn(Optional.of(questionProjection("0x" + "a".repeat(64), 1)));
    when(qnaProjectionPersistencePort.findAnswerByAnswerIdForUpdate(201L))
        .thenReturn(Optional.of(answerProjection("0x" + "b".repeat(64))));
    QnaEscrowExecutionPayload payload =
        new QnaEscrowExecutionPayload(
            QnaExecutionActionType.QNA_ANSWER_ACCEPT,
            101L,
            201L,
            "0x" + "1".repeat(40),
            "0x" + "2".repeat(40),
            new BigInteger("50000000000000000000"),
            "0x" + "c".repeat(64),
            "0x" + "d".repeat(64),
            "0x" + "3".repeat(40),
            "0x1234");

    adapter.afterExecutionConfirmed(
        intent(payload, ExecutionResourceType.QUESTION, "101", 7L), plan());

    verify(qnaAcceptStateSyncPort).confirmAccepted(101L, 201L);
    ArgumentCaptor<QnaAnswerProjection> answerCaptor =
        ArgumentCaptor.forClass(QnaAnswerProjection.class);
    verify(qnaProjectionPersistencePort).saveAnswer(answerCaptor.capture());
    assertThat(answerCaptor.getValue().isAccepted()).isTrue();
    assertThat(answerCaptor.getValue().getContentHash()).isEqualTo("0x" + "b".repeat(64));

    ArgumentCaptor<QnaQuestionProjection> questionCaptor =
        ArgumentCaptor.forClass(QnaQuestionProjection.class);
    verify(qnaProjectionPersistencePort).saveQuestion(questionCaptor.capture());
    assertThat(questionCaptor.getValue().getQuestionHash()).isEqualTo("0x" + "c".repeat(64));
    assertThat(questionCaptor.getValue().getAcceptedAnswerId())
        .isEqualTo(QnaEscrowIdCodec.answerId(201L));
    assertThat(questionCaptor.getValue().getState()).isEqualTo(QnaQuestionState.PAID_OUT);
  }

  @Test
  @DisplayName("afterExecutionFailedOnchain rolls back pending accept state")
  void afterExecutionFailedOnchain_rollsBackPendingAccept() throws Exception {
    QnaEscrowExecutionPayload payload =
        new QnaEscrowExecutionPayload(
            QnaExecutionActionType.QNA_ANSWER_ACCEPT,
            101L,
            201L,
            "0x" + "1".repeat(40),
            "0x" + "2".repeat(40),
            new BigInteger("50000000000000000000"),
            "0x" + "c".repeat(64),
            "0x" + "d".repeat(64),
            "0x" + "3".repeat(40),
            "0x1234");

    adapter.afterExecutionFailedOnchain(
        intent(payload, ExecutionResourceType.QUESTION, "101", 7L),
        plan(),
        "RECEIPT_STATUS_0");

    verify(qnaAcceptStateSyncPort).rollbackPendingAccept(101L, 201L);
  }

  private ExecutionIntent intent(
      QnaEscrowExecutionPayload payload,
      ExecutionResourceType resourceType,
      String resourceId,
      Long requesterUserId)
      throws Exception {
    return ExecutionIntent.create(
            "intent-1",
            "root-1",
            1,
            resourceType,
            resourceId,
            mapAction(payload.actionType()),
            requesterUserId,
            8L,
            ExecutionMode.EIP7702,
            "0x" + "e".repeat(64),
            objectMapper.writeValueAsString(payload),
            "0x" + "1".repeat(40),
            1L,
            "0x" + "2".repeat(40),
            LocalDateTime.of(2026, 4, 12, 10, 5),
            "0x" + "3".repeat(64),
            "0x" + "4".repeat(64),
            null,
            null,
            BigInteger.ZERO,
            LocalDate.of(2026, 4, 12),
            LocalDateTime.of(2026, 4, 12, 10, 0))
        .toBuilder()
        .submittedTxId(99L)
        .build()
        .markPendingOnchain(99L, LocalDateTime.of(2026, 4, 12, 10, 1))
        .confirm(LocalDateTime.of(2026, 4, 12, 10, 2));
  }

  private ExecutionActionPlan plan() {
    return new ExecutionActionPlan(
        BigInteger.ZERO,
        ExecutionReferenceType.USER_TO_SERVER,
        List.of(new ExecutionDraftCall("0x" + "1".repeat(40), BigInteger.ZERO, "0x1234")));
  }

  private QnaQuestionProjection questionProjection(String questionHash, int answerCount) {
    return QnaQuestionProjection.create(
            101L,
            7L,
            QnaEscrowIdCodec.questionId(101L),
            "0x" + "2".repeat(40),
            new BigInteger("50000000000000000000"),
            questionHash)
        .syncAnswerCount(answerCount);
  }

  private QnaAnswerProjection answerProjection(String contentHash) {
    return QnaAnswerProjection.create(
        201L,
        101L,
        QnaEscrowIdCodec.questionId(101L),
        QnaEscrowIdCodec.answerId(201L),
        22L,
        contentHash);
  }

  private ExecutionActionType mapAction(QnaExecutionActionType actionType) {
    return switch (actionType) {
      case QNA_QUESTION_CREATE -> ExecutionActionType.QNA_QUESTION_CREATE;
      case QNA_QUESTION_UPDATE -> ExecutionActionType.QNA_QUESTION_UPDATE;
      case QNA_QUESTION_DELETE -> ExecutionActionType.QNA_QUESTION_DELETE;
      case QNA_ANSWER_SUBMIT -> ExecutionActionType.QNA_ANSWER_SUBMIT;
      case QNA_ANSWER_UPDATE -> ExecutionActionType.QNA_ANSWER_UPDATE;
      case QNA_ANSWER_DELETE -> ExecutionActionType.QNA_ANSWER_DELETE;
      case QNA_ANSWER_ACCEPT -> ExecutionActionType.QNA_ANSWER_ACCEPT;
    };
  }
}
