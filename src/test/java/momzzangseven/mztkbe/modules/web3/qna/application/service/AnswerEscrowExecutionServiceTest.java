package momzzangseven.mztkbe.modules.web3.qna.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckAnswerCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraft;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaExecutionIntentStateView;
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
@DisplayName("AnswerEscrowExecutionService unit test")
class AnswerEscrowExecutionServiceTest {

  @Mock private QnaProjectionPersistencePort qnaProjectionPersistencePort;
  @Mock private LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort;
  @Mock private BuildQnaExecutionDraftPort buildQnaExecutionDraftPort;
  @Mock private SubmitQnaExecutionDraftPort submitQnaExecutionDraftPort;

  @InjectMocks private AnswerEscrowExecutionService service;

  @BeforeEach
  void setUp() {
    lenient()
        .when(loadQnaExecutionIntentStatePort.hasConflictingActiveIntent(any(), anyString(), any()))
        .thenReturn(false);
    lenient()
        .when(loadQnaExecutionIntentStatePort.loadLatestByRootIdempotencyKey(anyString()))
        .thenReturn(Optional.empty());
  }

  @Test
  @DisplayName("prepareAnswerCreate fails when the question is not registered onchain")
  void prepareAnswerCreate_failsWhenQuestionProjectionIsMissing() {
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.prepareAnswerCreate(
                    new PrepareAnswerCreateCommand(101L, 201L, 22L, 7L, "질문 본문", 50L, "답변 본문", 1)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question is not registered onchain yet");
  }

  @Test
  @DisplayName("prepareAnswerCreate submits draft without mutating projections")
  void prepareAnswerCreate_submitsWithoutPersistingProjections() {
    String storedQuestionHash = QnaContentHashFactory.hash("온체인 질문");
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(Optional.of(questionProjection(storedQuestionHash)));
    given(buildQnaExecutionDraftPort.build(any()))
        .willReturn(draft(QnaExecutionActionType.QNA_ANSWER_SUBMIT));
    given(submitQnaExecutionDraftPort.submit(any()))
        .willReturn(answerIntent("201", "intent-3", "QNA_ANSWER_SUBMIT"));

    service.prepareAnswerCreate(
        new PrepareAnswerCreateCommand(101L, 201L, 22L, 7L, "로컬 질문", 50L, "답변 본문", 1));

    ArgumentCaptor<QnaEscrowExecutionRequest> requestCaptor =
        ArgumentCaptor.forClass(QnaEscrowExecutionRequest.class);
    verify(buildQnaExecutionDraftPort).build(requestCaptor.capture());
    QnaEscrowExecutionRequest request = requestCaptor.getValue();
    assertThat(request.actionType()).isEqualTo(QnaExecutionActionType.QNA_ANSWER_SUBMIT);
    assertThat(request.resourceType()).isEqualTo(QnaExecutionResourceType.ANSWER);
    assertThat(request.resourceId()).isEqualTo("201");
    assertThat(request.tokenAddress()).isEqualTo("0x1111111111111111111111111111111111111111");
    assertThat(request.rewardAmountWei()).isEqualTo(new BigInteger("50000000000000000000"));
    assertThat(request.questionHash()).isEqualTo(storedQuestionHash);
    assertThat(request.contentHash()).isEqualTo(QnaContentHashFactory.hash("답변 본문"));

    verify(qnaProjectionPersistencePort, never()).saveQuestion(any());
    verify(qnaProjectionPersistencePort, never()).saveAnswer(any());
    verify(qnaProjectionPersistencePort, never()).deleteAnswerByAnswerId(any());
  }

  @Test
  @DisplayName("precheckAnswerCreate blocks when local question content differs from projection")
  void precheckAnswerCreate_blocksWhenQuestionHashDiffers() {
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(Optional.of(questionProjection("온체인 질문")));

    assertThatThrownBy(
            () -> service.precheckAnswerCreate(new PrecheckAnswerCreateCommand(101L, "로컬 질문")))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question content differs");
  }

  @Test
  @DisplayName(
      "precheckAnswerCreate blocks when the question already has an active on-chain mutation")
  void precheckAnswerCreate_blocksWhenQuestionHasActiveIntent() {
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(Optional.of(questionProjection("온체인 질문")));
    given(
            loadQnaExecutionIntentStatePort.loadLatestActiveByResource(
                QnaExecutionResourceType.QUESTION, "101"))
        .willReturn(
            Optional.of(
                new QnaExecutionIntentStateView(
                    "intent-active",
                    QnaExecutionActionType.QNA_QUESTION_DELETE,
                    ExecutionIntentStatus.AWAITING_SIGNATURE)));

    assertThatThrownBy(
            () -> service.precheckAnswerCreate(new PrecheckAnswerCreateCommand(101L, "온체인 질문")))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("active onchain mutation");
  }

  @Test
  @DisplayName(
      "recoverAnswerCreate recreates submit intent only after terminal submit and missing projection")
  void recoverAnswerCreate_recreatesWhenLatestSubmitIntentIsTerminal() {
    given(qnaProjectionPersistencePort.findAnswerByAnswerIdForUpdate(201L))
        .willReturn(Optional.empty());
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(Optional.of(questionProjection("온체인 질문")));
    given(loadQnaExecutionIntentStatePort.loadLatestByRootIdempotencyKey(anyString()))
        .willReturn(
            Optional.of(
                new QnaExecutionIntentStateView(
                    "intent-terminal",
                    QnaExecutionActionType.QNA_ANSWER_SUBMIT,
                    ExecutionIntentStatus.EXPIRED)));
    given(buildQnaExecutionDraftPort.build(any()))
        .willReturn(draft(QnaExecutionActionType.QNA_ANSWER_SUBMIT));
    given(submitQnaExecutionDraftPort.submit(any()))
        .willReturn(answerIntent("201", "intent-recovered", "QNA_ANSWER_SUBMIT"));

    QnaExecutionIntentResult result =
        service.recoverAnswerCreate(
            new PrepareAnswerCreateCommand(101L, 201L, 22L, 7L, "온체인 질문", 50L, "답변 본문", 1));

    assertThat(result.executionIntent().id()).isEqualTo("intent-recovered");
  }

  @Test
  @DisplayName("prepareAnswerUpdate fails when the answer is not registered onchain")
  void prepareAnswerUpdate_failsWhenAnswerProjectionIsMissing() {
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(Optional.of(questionProjection(QnaContentHashFactory.hash("질문 본문"))));
    given(qnaProjectionPersistencePort.findAnswerByAnswerIdForUpdate(201L))
        .willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.prepareAnswerUpdate(
                    new PrepareAnswerUpdateCommand(101L, 201L, 22L, 7L, "질문 본문", 50L, "수정된 답변", 1)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("answer is not registered onchain yet");
  }

  @Test
  @DisplayName("prepareAnswerUpdate uses stored reward projection instead of current config")
  void prepareAnswerUpdate_usesStoredRewardProjection() {
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(
            Optional.of(
                QnaQuestionProjection.create(
                    101L,
                    7L,
                    QnaEscrowIdCodec.questionId(101L),
                    "0x9999999999999999999999999999999999999999",
                    new BigInteger("123000000000000000000"),
                    QnaContentHashFactory.hash("온체인 질문"))));
    given(qnaProjectionPersistencePort.findAnswerByAnswerIdForUpdate(201L))
        .willReturn(
            Optional.of(
                QnaAnswerProjection.create(
                    201L,
                    101L,
                    QnaEscrowIdCodec.questionId(101L),
                    QnaEscrowIdCodec.answerId(201L),
                    22L,
                    QnaContentHashFactory.hash("온체인 답변"))));
    given(buildQnaExecutionDraftPort.build(any()))
        .willReturn(draft(QnaExecutionActionType.QNA_ANSWER_UPDATE));
    given(submitQnaExecutionDraftPort.submit(any()))
        .willReturn(answerIntent("201", "intent-update", "QNA_ANSWER_UPDATE"));

    service.prepareAnswerUpdate(
        new PrepareAnswerUpdateCommand(101L, 201L, 22L, 7L, "질문 본문", 50L, "수정된 답변", 1));

    ArgumentCaptor<QnaEscrowExecutionRequest> requestCaptor =
        ArgumentCaptor.forClass(QnaEscrowExecutionRequest.class);
    verify(buildQnaExecutionDraftPort).build(requestCaptor.capture());
    QnaEscrowExecutionRequest request = requestCaptor.getValue();
    assertThat(request.tokenAddress()).isEqualTo("0x9999999999999999999999999999999999999999");
    assertThat(request.rewardAmountWei()).isEqualTo(new BigInteger("123000000000000000000"));
    assertThat(request.contentHash()).isEqualTo(QnaContentHashFactory.hash("수정된 답변"));
  }

  @Test
  @DisplayName(
      "recoverAnswerUpdate recreates update intent when local answer content is newer than projection")
  void recoverAnswerUpdate_recreatesWhenProjectionStillStale() {
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(Optional.of(questionProjection("온체인 질문")));
    given(qnaProjectionPersistencePort.findAnswerByAnswerIdForUpdate(201L))
        .willReturn(Optional.of(answerProjection(QnaContentHashFactory.hash("온체인 답변"))));
    given(loadQnaExecutionIntentStatePort.loadLatestByRootIdempotencyKey(anyString()))
        .willReturn(
            Optional.of(
                new QnaExecutionIntentStateView(
                    "intent-terminal",
                    QnaExecutionActionType.QNA_ANSWER_UPDATE,
                    ExecutionIntentStatus.CANCELED)));
    given(buildQnaExecutionDraftPort.build(any()))
        .willReturn(draft(QnaExecutionActionType.QNA_ANSWER_UPDATE));
    given(submitQnaExecutionDraftPort.submit(any()))
        .willReturn(answerIntent("201", "intent-recovered-update", "QNA_ANSWER_UPDATE"));

    Optional<QnaExecutionIntentResult> result =
        service.recoverAnswerUpdate(
            new PrepareAnswerUpdateCommand(101L, 201L, 22L, 7L, "질문 본문", 50L, "수정된 답변", 1));

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().executionIntent().id()).isEqualTo("intent-recovered-update");
  }

  @Test
  @DisplayName("prepareAnswerDelete uses stored question hash and does not mutate projections")
  void prepareAnswerDelete_usesStoredQuestionHashWithoutMutation() {
    String storedQuestionHash = QnaContentHashFactory.hash("온체인 질문");
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(Optional.of(questionProjection(storedQuestionHash)));
    given(qnaProjectionPersistencePort.findAnswerByAnswerIdForUpdate(201L))
        .willReturn(
            Optional.of(
                QnaAnswerProjection.create(
                    201L,
                    101L,
                    QnaEscrowIdCodec.questionId(101L),
                    QnaEscrowIdCodec.answerId(201L),
                    22L,
                    QnaContentHashFactory.hash("온체인 답변"))));
    given(buildQnaExecutionDraftPort.build(any()))
        .willReturn(draft(QnaExecutionActionType.QNA_ANSWER_DELETE));
    given(submitQnaExecutionDraftPort.submit(any()))
        .willReturn(answerIntent("201", "intent-4", "QNA_ANSWER_DELETE"));

    service.prepareAnswerDelete(
        new PrepareAnswerDeleteCommand(101L, 201L, 22L, 7L, "로컬 질문", 50L, 0));

    ArgumentCaptor<QnaEscrowExecutionRequest> requestCaptor =
        ArgumentCaptor.forClass(QnaEscrowExecutionRequest.class);
    verify(buildQnaExecutionDraftPort).build(requestCaptor.capture());
    QnaEscrowExecutionRequest request = requestCaptor.getValue();
    assertThat(request.actionType()).isEqualTo(QnaExecutionActionType.QNA_ANSWER_DELETE);
    assertThat(request.tokenAddress()).isEqualTo("0x1111111111111111111111111111111111111111");
    assertThat(request.rewardAmountWei()).isEqualTo(new BigInteger("50000000000000000000"));
    assertThat(request.questionHash()).isEqualTo(storedQuestionHash);
    assertThat(request.questionHash()).isNotEqualTo(QnaContentHashFactory.hash("로컬 질문"));
    assertThat(request.contentHash()).isNull();

    verify(qnaProjectionPersistencePort, never()).saveQuestion(any());
    verify(qnaProjectionPersistencePort, never()).saveAnswer(any());
    verify(qnaProjectionPersistencePort, never()).deleteAnswerByAnswerId(any());
  }

  @Test
  @DisplayName("prepareAnswerDelete blocks when another active answer intent exists")
  void prepareAnswerDelete_blocksWhenConflictingIntentExists() {
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(Optional.of(questionProjection("온체인 질문")));
    given(qnaProjectionPersistencePort.findAnswerByAnswerIdForUpdate(201L))
        .willReturn(
            Optional.of(
                QnaAnswerProjection.create(
                    201L,
                    101L,
                    QnaEscrowIdCodec.questionId(101L),
                    QnaEscrowIdCodec.answerId(201L),
                    22L,
                    QnaContentHashFactory.hash("온체인 답변"))));
    given(
            loadQnaExecutionIntentStatePort.hasConflictingActiveIntent(
                QnaExecutionResourceType.ANSWER, "201", QnaExecutionActionType.QNA_ANSWER_DELETE))
        .willReturn(true);

    assertThatThrownBy(
            () ->
                service.prepareAnswerDelete(
                    new PrepareAnswerDeleteCommand(101L, 201L, 22L, 7L, "온체인 질문", 50L, 0)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("conflicting active answer execution intent");
  }

  private QnaQuestionProjection questionProjection(String questionHash) {
    return QnaQuestionProjection.create(
        101L,
        7L,
        QnaEscrowIdCodec.questionId(101L),
        "0x1111111111111111111111111111111111111111",
        new BigInteger("50000000000000000000"),
        questionHash);
  }

  private QnaAnswerProjection answerProjection(String answerHash) {
    return QnaAnswerProjection.create(
        201L,
        101L,
        QnaEscrowIdCodec.questionId(101L),
        QnaEscrowIdCodec.answerId(201L),
        22L,
        answerHash);
  }

  private QnaExecutionDraft draft(QnaExecutionActionType actionType) {
    return new QnaExecutionDraft(
        QnaExecutionResourceType.ANSWER,
        "201",
        QnaExecutionResourceStatus.PENDING_EXECUTION,
        actionType,
        22L,
        7L,
        "root-key",
        "0x" + "a".repeat(64),
        "{}",
        List.of(
            new QnaExecutionDraftCall(
                "0x1111111111111111111111111111111111111111", BigInteger.ZERO, "0x1234")),
        true,
        "0x2222222222222222222222222222222222222222",
        1L,
        "0x3333333333333333333333333333333333333333",
        "0x" + "b".repeat(64),
        null,
        "0x" + "c".repeat(64),
        LocalDateTime.now().plusMinutes(5));
  }

  private QnaExecutionIntentResult answerIntent(
      String resourceId, String intentId, String actionType) {
    return new QnaExecutionIntentResult(
        new QnaExecutionIntentResult.Resource("ANSWER", resourceId, "PENDING_EXECUTION"),
        actionType,
        new QnaExecutionIntentResult.ExecutionIntent(
            intentId, "AWAITING_SIGNATURE", LocalDateTime.of(2026, 4, 14, 10, 0)),
        new QnaExecutionIntentResult.Execution("EIP7702", 2),
        null,
        false);
  }
}
