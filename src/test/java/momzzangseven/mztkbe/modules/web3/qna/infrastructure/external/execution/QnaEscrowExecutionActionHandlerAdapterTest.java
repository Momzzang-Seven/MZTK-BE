package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAcceptStateSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAdminRefundStateSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaExecutionIntentStateView;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaLocalDeleteSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionPublicationSyncPort;
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
  @Mock private QnaAdminRefundStateSyncPort qnaAdminRefundStateSyncPort;
  @Mock private QnaQuestionPublicationSyncPort qnaQuestionPublicationSyncPort;
  @Mock private LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort;
  @Mock private QnaLocalDeleteSyncPort qnaLocalDeleteSyncPort;

  private QnaEscrowExecutionActionHandlerAdapter adapter;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    adapter =
        new QnaEscrowExecutionActionHandlerAdapter(
            objectMapper,
            qnaProjectionPersistencePort,
            qnaAcceptStateSyncPort,
            qnaAdminRefundStateSyncPort,
            qnaQuestionPublicationSyncPort,
            loadQnaExecutionIntentStatePort,
            qnaLocalDeleteSyncPort);
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
    verify(qnaQuestionPublicationSyncPort).confirmQuestionCreated(101L, "intent-1");
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
    verify(qnaLocalDeleteSyncPort).confirmAnswerDeleted(201L);
  }

  @Test
  @DisplayName("afterExecutionConfirmed marks deleted question and syncs local hard delete")
  void afterExecutionConfirmed_marksDeletedQuestionAndSyncsLocalDelete() throws Exception {
    when(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .thenReturn(Optional.of(questionProjection("0x" + "a".repeat(64), 0)));
    QnaEscrowExecutionPayload payload =
        new QnaEscrowExecutionPayload(
            QnaExecutionActionType.QNA_QUESTION_DELETE,
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

    ArgumentCaptor<QnaQuestionProjection> questionCaptor =
        ArgumentCaptor.forClass(QnaQuestionProjection.class);
    verify(qnaProjectionPersistencePort).saveQuestion(questionCaptor.capture());
    assertThat(questionCaptor.getValue().getState()).isEqualTo(QnaQuestionState.DELETED);
    verify(qnaLocalDeleteSyncPort).confirmQuestionDeleted(101L);
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
  @DisplayName("afterExecutionConfirmed marks admin settled using stored answer key")
  void afterExecutionConfirmed_marksAdminSettled() throws Exception {
    when(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .thenReturn(Optional.of(questionProjection("0x" + "a".repeat(64), 1)));
    when(qnaProjectionPersistencePort.findAnswerByAnswerIdForUpdate(201L))
        .thenReturn(Optional.of(answerProjection("0x" + "b".repeat(64))));

    adapter.afterExecutionConfirmed(
        intent(adminSettlePayload(), ExecutionResourceType.QUESTION, "101", 7L), plan());

    verify(qnaAcceptStateSyncPort).confirmAccepted(101L, 201L);
    ArgumentCaptor<QnaQuestionProjection> questionCaptor =
        ArgumentCaptor.forClass(QnaQuestionProjection.class);
    verify(qnaProjectionPersistencePort).saveQuestion(questionCaptor.capture());
    assertThat(questionCaptor.getValue().getAcceptedAnswerId())
        .isEqualTo(QnaEscrowIdCodec.answerId(201L));
    assertThat(questionCaptor.getValue().getState()).isEqualTo(QnaQuestionState.ADMIN_SETTLED);
  }

  @Test
  @DisplayName("afterExecutionConfirmed marks refunded question as deleted when no answers remain")
  void afterExecutionConfirmed_marksAdminRefundDeleted() throws Exception {
    when(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .thenReturn(Optional.of(questionProjection("0x" + "a".repeat(64), 0)));

    adapter.afterExecutionConfirmed(
        intent(adminRefundPayload(), ExecutionResourceType.QUESTION, "101", 7L), plan());

    ArgumentCaptor<QnaQuestionProjection> questionCaptor =
        ArgumentCaptor.forClass(QnaQuestionProjection.class);
    verify(qnaProjectionPersistencePort).saveQuestion(questionCaptor.capture());
    assertThat(questionCaptor.getValue().getState()).isEqualTo(QnaQuestionState.DELETED);
    verify(qnaLocalDeleteSyncPort).confirmQuestionDeleted(101L);
  }

  @Test
  @DisplayName(
      "afterExecutionConfirmed marks refunded question as deleted_with_answers when answers remain")
  void afterExecutionConfirmed_marksAdminRefundDeletedWithAnswers() throws Exception {
    when(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .thenReturn(Optional.of(questionProjection("0x" + "a".repeat(64), 2)));

    adapter.afterExecutionConfirmed(
        intent(adminRefundPayload(), ExecutionResourceType.QUESTION, "101", 7L), plan());

    ArgumentCaptor<QnaQuestionProjection> questionCaptor =
        ArgumentCaptor.forClass(QnaQuestionProjection.class);
    verify(qnaProjectionPersistencePort).saveQuestion(questionCaptor.capture());
    assertThat(questionCaptor.getValue().getState())
        .isEqualTo(QnaQuestionState.DELETED_WITH_ANSWERS);
    verify(qnaLocalDeleteSyncPort).confirmQuestionDeleted(101L);
  }

  @Test
  @DisplayName(
      "afterExecutionFailedOnchain defers rollback to terminal callback when failure reason is missing")
  void afterExecutionFailedOnchain_defersRollbackWhenFailureReasonIsNull() throws Exception {
    adapter.afterExecutionFailedOnchain(
        intent(acceptPayload(), ExecutionResourceType.QUESTION, "101", 7L), plan(), null);

    verifyNoInteractions(qnaAcceptStateSyncPort, qnaAdminRefundStateSyncPort);
  }

  @Test
  @DisplayName("afterExecutionTerminated rolls back pending accept when failure reason is blank")
  void afterExecutionTerminated_rollsBackWhenFailureReasonIsBlank() throws Exception {
    adapter.afterExecutionTerminated(
        intent(acceptPayload(), ExecutionResourceType.QUESTION, "101", 7L),
        plan(),
        ExecutionIntentStatus.FAILED_ONCHAIN,
        " ");

    verify(qnaAcceptStateSyncPort).rollbackPendingAccept(101L, 201L);
  }

  @Test
  @DisplayName("afterExecutionTerminated rolls back pending accept for non retryable failure")
  void afterExecutionTerminated_rollsBackForNonRetryableFailure() throws Exception {
    adapter.afterExecutionTerminated(
        intent(acceptPayload(), ExecutionResourceType.QUESTION, "101", 7L),
        plan(),
        ExecutionIntentStatus.FAILED_ONCHAIN,
        "TREASURY_TOKEN_INSUFFICIENT");

    verify(qnaAcceptStateSyncPort).rollbackPendingAccept(101L, 201L);
  }

  @Test
  @DisplayName("afterExecutionTerminated keeps pending accept for retryable failure")
  void afterExecutionTerminated_keepsPendingForRetryableFailure() throws Exception {
    adapter.afterExecutionTerminated(
        intent(acceptPayload(), ExecutionResourceType.QUESTION, "101", 7L),
        plan(),
        ExecutionIntentStatus.FAILED_ONCHAIN,
        "RPC_UNAVAILABLE");

    verify(qnaAcceptStateSyncPort, never()).rollbackPendingAccept(101L, 201L);
  }

  @Test
  @DisplayName("afterExecutionTerminated rolls back pending accept for unknown onchain failure")
  void afterExecutionTerminated_rollsBackForUnknownFailure() throws Exception {
    adapter.afterExecutionTerminated(
        intent(acceptPayload(), ExecutionResourceType.QUESTION, "101", 7L),
        plan(),
        ExecutionIntentStatus.FAILED_ONCHAIN,
        "EXPIRED");

    verify(qnaAcceptStateSyncPort).rollbackPendingAccept(101L, 201L);
  }

  @Test
  @DisplayName("afterExecutionTerminated rolls back pending accept for suffixed failure code")
  void afterExecutionTerminated_rollsBackForSuffixedFailureCode() throws Exception {
    adapter.afterExecutionTerminated(
        intent(acceptPayload(), ExecutionResourceType.QUESTION, "101", 7L),
        plan(),
        ExecutionIntentStatus.FAILED_ONCHAIN,
        "RECEIPT_TIMEOUT_30S");

    verify(qnaAcceptStateSyncPort).rollbackPendingAccept(101L, 201L);
  }

  @Test
  @DisplayName("afterExecutionTerminated ignores non accept action")
  void afterExecutionTerminated_ignoresNonAcceptAction() throws Exception {
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

    adapter.afterExecutionTerminated(
        intent(payload, ExecutionResourceType.QUESTION, "101", 7L),
        plan(),
        ExecutionIntentStatus.FAILED_ONCHAIN,
        "TREASURY_TOKEN_INSUFFICIENT");

    verifyNoInteractions(qnaAcceptStateSyncPort, qnaAdminRefundStateSyncPort);
  }

  @Test
  @DisplayName("afterExecutionTerminated rolls back pending accept when intent expires")
  void afterExecutionTerminated_rollsBackPendingAcceptOnExpired() throws Exception {
    adapter.afterExecutionTerminated(
        intent(acceptPayload(), ExecutionResourceType.QUESTION, "101", 7L),
        plan(),
        ExecutionIntentStatus.EXPIRED,
        "EXECUTION_INTENT_EXPIRED");

    verify(qnaAcceptStateSyncPort).rollbackPendingAccept(101L, 201L);
  }

  @Test
  @DisplayName("afterExecutionTerminated rolls back pending accept when intent is canceled")
  void afterExecutionTerminated_rollsBackPendingAcceptOnCanceled() throws Exception {
    adapter.afterExecutionTerminated(
        intent(acceptPayload(), ExecutionResourceType.QUESTION, "101", 7L),
        plan(),
        ExecutionIntentStatus.CANCELED,
        "SUPERSEDED_BY_NEW_PAYLOAD");

    verify(qnaAcceptStateSyncPort).rollbackPendingAccept(101L, 201L);
  }

  @Test
  @DisplayName("afterExecutionTerminated rolls back pending accept on nonce stale")
  void afterExecutionTerminated_rollsBackPendingAcceptOnNonceStale() throws Exception {
    adapter.afterExecutionTerminated(
        intent(acceptPayload(), ExecutionResourceType.QUESTION, "101", 7L),
        plan(),
        ExecutionIntentStatus.NONCE_STALE,
        "AUTH_NONCE_MISMATCH");

    verify(qnaAcceptStateSyncPort).rollbackPendingAccept(101L, 201L);
  }

  @Test
  @DisplayName("afterExecutionTerminated rolls back admin settle pending accept on expire")
  void afterExecutionTerminated_rollsBackAdminSettleOnExpired() throws Exception {
    adapter.afterExecutionTerminated(
        intent(adminSettlePayload(), ExecutionResourceType.QUESTION, "101", 7L),
        plan(),
        ExecutionIntentStatus.EXPIRED,
        "EXECUTION_INTENT_EXPIRED");

    verify(qnaAcceptStateSyncPort).rollbackPendingAccept(101L, 201L);
  }

  @Test
  @DisplayName("afterExecutionTerminated rolls back pending admin refund on expire")
  void afterExecutionTerminated_rollsBackPendingAdminRefundOnExpired() throws Exception {
    adapter.afterExecutionTerminated(
        intent(adminRefundPayload(), ExecutionResourceType.QUESTION, "101", 7L),
        plan(),
        ExecutionIntentStatus.EXPIRED,
        "EXECUTION_INTENT_EXPIRED");

    verify(qnaAdminRefundStateSyncPort).rollbackPendingRefund(101L);
  }

  @Test
  @DisplayName("afterExecutionTerminated rolls back pending admin refund for non retryable failure")
  void afterExecutionTerminated_rollsBackPendingAdminRefundForNonRetryableFailure()
      throws Exception {
    adapter.afterExecutionTerminated(
        intent(adminRefundPayload(), ExecutionResourceType.QUESTION, "101", 7L),
        plan(),
        ExecutionIntentStatus.FAILED_ONCHAIN,
        "TREASURY_TOKEN_INSUFFICIENT");

    verify(qnaAdminRefundStateSyncPort).rollbackPendingRefund(101L);
  }

  @Test
  @DisplayName("afterExecutionTerminated keeps pending admin refund for retryable failure")
  void afterExecutionTerminated_keepsPendingAdminRefundForRetryableFailure() throws Exception {
    adapter.afterExecutionTerminated(
        intent(adminRefundPayload(), ExecutionResourceType.QUESTION, "101", 7L),
        plan(),
        ExecutionIntentStatus.FAILED_ONCHAIN,
        "RPC_UNAVAILABLE");

    verify(qnaAdminRefundStateSyncPort, never()).rollbackPendingRefund(101L);
  }

  @Test
  @DisplayName("afterExecutionTerminated syncs question create failure when projection is missing")
  void afterExecutionTerminated_syncsQuestionCreateFailure() throws Exception {
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
    when(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .thenReturn(Optional.empty());
    when(loadQnaExecutionIntentStatePort.loadLatestByRootIdempotencyKey(
            "qna:qna_question_create:7:101"))
        .thenReturn(
            Optional.of(
                new QnaExecutionIntentStateView(
                    "intent-1",
                    QnaExecutionActionType.QNA_QUESTION_CREATE,
                    ExecutionIntentStatus.EXPIRED)));

    adapter.afterExecutionTerminated(
        intent(payload, ExecutionResourceType.QUESTION, "101", 7L),
        plan(),
        ExecutionIntentStatus.EXPIRED,
        "expired");

    verify(qnaQuestionPublicationSyncPort)
        .failQuestionCreate(101L, "intent-1", ExecutionIntentStatus.EXPIRED, "expired");
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

  private QnaEscrowExecutionPayload acceptPayload() {
    return new QnaEscrowExecutionPayload(
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
  }

  private QnaEscrowExecutionPayload adminRefundPayload() {
    return new QnaEscrowExecutionPayload(
        QnaExecutionActionType.QNA_ADMIN_REFUND,
        101L,
        null,
        "0x" + "1".repeat(40),
        "0x" + "2".repeat(40),
        new BigInteger("50000000000000000000"),
        "0x" + "a".repeat(64),
        null,
        "0x" + "3".repeat(40),
        "0x1234");
  }

  private QnaEscrowExecutionPayload adminSettlePayload() {
    return new QnaEscrowExecutionPayload(
        QnaExecutionActionType.QNA_ADMIN_SETTLE,
        101L,
        201L,
        "0x" + "1".repeat(40),
        "0x" + "2".repeat(40),
        new BigInteger("50000000000000000000"),
        "0x" + "c".repeat(64),
        "0x" + "d".repeat(64),
        "0x" + "3".repeat(40),
        "0x1234");
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
      case QNA_ADMIN_SETTLE -> ExecutionActionType.QNA_ADMIN_SETTLE;
      case QNA_ADMIN_REFUND -> ExecutionActionType.QNA_ADMIN_REFUND;
    };
  }
}
