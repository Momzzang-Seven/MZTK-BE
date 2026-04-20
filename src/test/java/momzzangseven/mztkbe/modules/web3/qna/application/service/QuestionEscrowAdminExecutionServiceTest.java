package momzzangseven.mztkbe.modules.web3.qna.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAdminSettleCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraft;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaUnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaAdminExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAnswerIdsPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.SubmitQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaAnswerProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuestionEscrowAdminExecutionService unit test")
class QuestionEscrowAdminExecutionServiceTest {

  @Mock private QnaProjectionPersistencePort qnaProjectionPersistencePort;
  @Mock private LoadQnaAnswerIdsPort loadQnaAnswerIdsPort;
  @Mock private LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort;
  @Mock private BuildQnaAdminExecutionDraftPort buildQnaAdminExecutionDraftPort;
  @Mock private SubmitQnaExecutionDraftPort submitQnaExecutionDraftPort;

  @InjectMocks private QuestionEscrowAdminExecutionService service;

  @BeforeEach
  void setUp() {
    given(loadQnaExecutionIntentStatePort.hasConflictingActiveIntent(any(), any(), any()))
        .willReturn(false);
    lenient().when(loadQnaAnswerIdsPort.loadAnswerIdsByPostId(any())).thenReturn(List.of());
    lenient()
        .when(buildQnaAdminExecutionDraftPort.build(any()))
        .thenReturn(adminDraft(QnaExecutionActionType.QNA_ADMIN_SETTLE));
    lenient()
        .when(submitQnaExecutionDraftPort.submit(any()))
        .thenReturn(executionIntentResult("intent-1"));
  }

  @Test
  @DisplayName("prepareAdminSettle builds direct admin execution draft")
  void prepareAdminSettle_buildsDirectAdminExecutionDraft() {
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(Optional.of(questionProjection("질문")));
    given(qnaProjectionPersistencePort.findAnswerByAnswerIdForUpdate(201L))
        .willReturn(Optional.of(answerProjection("답변")));

    QnaExecutionIntentResult result =
        service.prepareAdminSettle(
            new PrepareAdminSettleCommand(101L, 201L, 7L, 22L, "질문", "답변"));

    ArgumentCaptor<QnaEscrowExecutionRequest> requestCaptor =
        ArgumentCaptor.forClass(QnaEscrowExecutionRequest.class);
    verify(buildQnaAdminExecutionDraftPort).build(requestCaptor.capture());
    QnaEscrowExecutionRequest request = requestCaptor.getValue();
    assertThat(request.actionType()).isEqualTo(QnaExecutionActionType.QNA_ADMIN_SETTLE);
    assertThat(request.postId()).isEqualTo(101L);
    assertThat(request.answerId()).isEqualTo(201L);
    assertThat(result.executionIntent().id()).isEqualTo("intent-1");
  }

  @Test
  @DisplayName("prepareAdminRefund blocks when any answer under the question has active intent")
  void prepareAdminRefund_blocksWhenQuestionAnswerHasActiveIntent() {
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(Optional.of(questionProjection("질문")));
    given(loadQnaAnswerIdsPort.loadAnswerIdsByPostId(101L)).willReturn(List.of(201L));
    given(loadQnaExecutionIntentStatePort.hasActiveIntentForUpdate(
            QnaExecutionResourceType.ANSWER, "201"))
        .willReturn(true);

    assertThatThrownBy(() -> service.prepareAdminRefund(new PrepareAdminRefundCommand(101L, 7L)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("conflicting active answer execution intent exists for refund");
  }

  @Test
  @DisplayName("prepareAdminRefund blocks when a local-only answer has an active intent")
  void prepareAdminRefund_blocksWhenLocalOnlyAnswerHasActiveIntent() {
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(Optional.of(questionProjection("질문")));
    given(loadQnaAnswerIdsPort.loadAnswerIdsByPostId(101L)).willReturn(List.of(301L));
    given(qnaProjectionPersistencePort.findAnswersByPostIdForUpdate(101L)).willReturn(List.of());
    given(loadQnaExecutionIntentStatePort.hasActiveIntentForUpdate(
            QnaExecutionResourceType.ANSWER, "301"))
        .willReturn(true);

    assertThatThrownBy(() -> service.prepareAdminRefund(new PrepareAdminRefundCommand(101L, 7L)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("conflicting active answer execution intent exists for refund");
  }

  private QnaQuestionProjection questionProjection(String content) {
    return QnaQuestionProjection.create(
        101L,
        7L,
        QnaEscrowIdCodec.questionId(101L),
        "0x4444444444444444444444444444444444444444",
        new BigInteger("50000000000000000000"),
        QnaContentHashFactory.hash(content));
  }

  private QnaAnswerProjection answerProjection(String content) {
    return QnaAnswerProjection.create(
        201L,
        101L,
        QnaEscrowIdCodec.questionId(101L),
        QnaEscrowIdCodec.answerId(201L),
        22L,
        QnaContentHashFactory.hash(content));
  }

  private QnaExecutionDraft adminDraft(QnaExecutionActionType actionType) {
    return new QnaExecutionDraft(
        QnaExecutionResourceType.QUESTION,
        "101",
        QnaExecutionResourceStatus.PENDING_EXECUTION,
        actionType,
        7L,
        22L,
        "root",
        "0x" + "a".repeat(64),
        "{\"payload\":true}",
        List.of(new QnaExecutionDraftCall("0x" + "3".repeat(40), BigInteger.ZERO, "0x1234")),
        false,
        null,
        null,
        null,
        null,
        new QnaUnsignedTxSnapshot(
            11155111L,
            "0x" + "4".repeat(40),
            "0x" + "3".repeat(40),
            BigInteger.ZERO,
            "0x1234",
            12L,
            BigInteger.valueOf(210_000),
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(30_000_000_000L)),
        "0x" + "b".repeat(64),
        LocalDateTime.of(2026, 4, 20, 12, 0));
  }

  private QnaExecutionIntentResult executionIntentResult(String intentId) {
    return new QnaExecutionIntentResult(
        new QnaExecutionIntentResult.Resource("QUESTION", "101", "PENDING_EXECUTION"),
        "QNA_ADMIN_SETTLE",
        new QnaExecutionIntentResult.ExecutionIntent(
            intentId, "AWAITING_SIGNATURE", LocalDateTime.of(2026, 4, 20, 12, 0)),
        new QnaExecutionIntentResult.Execution("EIP1559", 1),
        null,
        false);
  }
}
