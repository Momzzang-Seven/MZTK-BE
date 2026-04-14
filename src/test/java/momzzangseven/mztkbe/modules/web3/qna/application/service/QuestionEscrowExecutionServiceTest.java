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
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerAcceptCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraft;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaRewardTokenConfigPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.PrecheckQuestionFundingPort;
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
@DisplayName("QuestionEscrowExecutionService unit test")
class QuestionEscrowExecutionServiceTest {

  @Mock private PrecheckQuestionFundingPort precheckQuestionFundingPort;
  @Mock private LoadQnaRewardTokenConfigPort loadQnaRewardTokenConfigPort;
  @Mock private QnaProjectionPersistencePort qnaProjectionPersistencePort;
  @Mock private LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort;
  @Mock private BuildQnaExecutionDraftPort buildQnaExecutionDraftPort;
  @Mock private SubmitQnaExecutionDraftPort submitQnaExecutionDraftPort;

  @InjectMocks private QuestionEscrowExecutionService service;

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
  @DisplayName(
      "prepareQuestionCreate submits create execution intent without persisting projection")
  void prepareQuestionCreate_submitsWithoutPersistingProjection() {
    given(loadQnaRewardTokenConfigPort.loadRewardTokenConfig())
        .willReturn(
            new LoadQnaRewardTokenConfigPort.RewardTokenConfig(
                "0x1111111111111111111111111111111111111111", 18));
    given(buildQnaExecutionDraftPort.build(any()))
        .willReturn(draft(QnaExecutionActionType.QNA_QUESTION_CREATE));
    given(submitQnaExecutionDraftPort.submit(any()))
        .willReturn(questionIntent("101", "intent-1", "QNA_QUESTION_CREATE"));

    QnaExecutionIntentResult result =
        service.prepareQuestionCreate(new PrepareQuestionCreateCommand(101L, 7L, "질문 본문", 50L));

    ArgumentCaptor<QnaEscrowExecutionRequest> requestCaptor =
        ArgumentCaptor.forClass(QnaEscrowExecutionRequest.class);
    verify(buildQnaExecutionDraftPort).build(requestCaptor.capture());
    QnaEscrowExecutionRequest request = requestCaptor.getValue();
    assertThat(request.resourceType()).isEqualTo(QnaExecutionResourceType.QUESTION);
    assertThat(request.resourceId()).isEqualTo("101");
    assertThat(request.actionType()).isEqualTo(QnaExecutionActionType.QNA_QUESTION_CREATE);
    assertThat(request.postId()).isEqualTo(101L);
    assertThat(request.questionHash()).isEqualTo(QnaContentHashFactory.hash("질문 본문"));
    assertThat(request.contentHash()).isNull();

    verify(qnaProjectionPersistencePort, never()).saveQuestion(any());
    verify(qnaProjectionPersistencePort, never()).saveAnswer(any());
    assertThat(result.executionIntent().id()).isEqualTo("intent-1");
  }

  @Test
  @DisplayName("prepareQuestionUpdate fails when the question is not registered onchain")
  void prepareQuestionUpdate_failsWhenProjectionIsMissing() {
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.prepareQuestionUpdate(
                    new PrepareQuestionUpdateCommand(101L, 7L, "수정된 질문", 50L)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question is not registered onchain yet");
  }

  @Test
  @DisplayName("prepareQuestionUpdate uses stored reward projection instead of current config")
  void prepareQuestionUpdate_usesStoredRewardProjection() {
    String storedQuestionHash = QnaContentHashFactory.hash("온체인 질문");
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(
            Optional.of(
                QnaQuestionProjection.create(
                    101L,
                    7L,
                    QnaEscrowIdCodec.questionId(101L),
                    "0x9999999999999999999999999999999999999999",
                    new BigInteger("123000000000000000000"),
                    storedQuestionHash)));
    given(buildQnaExecutionDraftPort.build(any()))
        .willReturn(draft(QnaExecutionActionType.QNA_QUESTION_UPDATE));
    given(submitQnaExecutionDraftPort.submit(any()))
        .willReturn(questionIntent("101", "intent-update", "QNA_QUESTION_UPDATE"));

    service.prepareQuestionUpdate(new PrepareQuestionUpdateCommand(101L, 7L, "수정된 질문", 50L));

    ArgumentCaptor<QnaEscrowExecutionRequest> requestCaptor =
        ArgumentCaptor.forClass(QnaEscrowExecutionRequest.class);
    verify(buildQnaExecutionDraftPort).build(requestCaptor.capture());
    QnaEscrowExecutionRequest request = requestCaptor.getValue();
    assertThat(request.tokenAddress()).isEqualTo("0x9999999999999999999999999999999999999999");
    assertThat(request.rewardAmountWei()).isEqualTo(new BigInteger("123000000000000000000"));
    assertThat(request.questionHash()).isEqualTo(QnaContentHashFactory.hash("수정된 질문"));
  }

  @Test
  @DisplayName(
      "recoverQuestionUpdate recreates update intent when local content is newer than projection")
  void recoverQuestionUpdate_recreatesWhenProjectionStillStale() {
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(Optional.of(questionProjection("온체인 질문", 0)));
    given(loadQnaExecutionIntentStatePort.loadLatestByRootIdempotencyKey(anyString()))
        .willReturn(
            Optional.of(
                new QnaExecutionIntentStateView(
                    "intent-terminal",
                    QnaExecutionActionType.QNA_QUESTION_UPDATE,
                    ExecutionIntentStatus.FAILED_ONCHAIN)));
    given(buildQnaExecutionDraftPort.build(any()))
        .willReturn(draft(QnaExecutionActionType.QNA_QUESTION_UPDATE));
    given(submitQnaExecutionDraftPort.submit(any()))
        .willReturn(questionIntent("101", "intent-recovered-update", "QNA_QUESTION_UPDATE"));

    Optional<QnaExecutionIntentResult> result =
        service.recoverQuestionUpdate(new PrepareQuestionUpdateCommand(101L, 7L, "수정된 질문", 50L));

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().executionIntent().id()).isEqualTo("intent-recovered-update");
  }

  @Test
  @DisplayName(
      "recoverQuestionCreate recreates create intent only after terminal create and missing projection")
  void recoverQuestionCreate_recreatesWhenLatestCreateIntentIsTerminal() {
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(Optional.empty());
    given(loadQnaExecutionIntentStatePort.loadLatestByRootIdempotencyKey(anyString()))
        .willReturn(
            Optional.of(
                new QnaExecutionIntentStateView(
                    "intent-terminal",
                    QnaExecutionActionType.QNA_QUESTION_CREATE,
                    ExecutionIntentStatus.CANCELED)));
    given(loadQnaRewardTokenConfigPort.loadRewardTokenConfig())
        .willReturn(
            new LoadQnaRewardTokenConfigPort.RewardTokenConfig(
                "0x1111111111111111111111111111111111111111", 18));
    given(buildQnaExecutionDraftPort.build(any()))
        .willReturn(draft(QnaExecutionActionType.QNA_QUESTION_CREATE));
    given(submitQnaExecutionDraftPort.submit(any()))
        .willReturn(questionIntent("101", "intent-recovered", "QNA_QUESTION_CREATE"));

    QnaExecutionIntentResult result =
        service.recoverQuestionCreate(new PrepareQuestionCreateCommand(101L, 7L, "질문 본문", 50L));

    assertThat(result.executionIntent().id()).isEqualTo("intent-recovered");
    verify(loadQnaExecutionIntentStatePort).loadLatestByRootIdempotencyKey(anyString());
  }

  @Test
  @DisplayName("prepareQuestionUpdate blocks when onchain projection still has answers")
  void prepareQuestionUpdate_blocksWhenProjectionHasAnswers() {
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(Optional.of(questionProjection("온체인 질문", 1)));

    assertThatThrownBy(
            () ->
                service.prepareQuestionUpdate(
                    new PrepareQuestionUpdateCommand(101L, 7L, "수정된 질문", 50L)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("unresolved onchain answers");
  }

  @Test
  @DisplayName("prepareQuestionUpdate blocks when another active question intent exists")
  void prepareQuestionUpdate_blocksWhenConflictingIntentExists() {
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(Optional.of(questionProjection("온체인 질문", 0)));
    given(
            loadQnaExecutionIntentStatePort.hasConflictingActiveIntent(
                QnaExecutionResourceType.QUESTION,
                "101",
                QnaExecutionActionType.QNA_QUESTION_UPDATE))
        .willReturn(true);

    assertThatThrownBy(
            () ->
                service.prepareQuestionUpdate(
                    new PrepareQuestionUpdateCommand(101L, 7L, "수정된 질문", 50L)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("conflicting active question execution intent");
  }

  @Test
  @DisplayName("prepareAnswerAccept uses stored projection hashes and does not mutate projection")
  void prepareAnswerAccept_usesStoredProjectionHashes() {
    String storedQuestionHash = QnaContentHashFactory.hash("온체인 질문");
    String storedAnswerHash = QnaContentHashFactory.hash("온체인 답변");
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(
            Optional.of(
                QnaQuestionProjection.create(
                    101L,
                    7L,
                    QnaEscrowIdCodec.questionId(101L),
                    "0x1111111111111111111111111111111111111111",
                    new BigInteger("50000000000000000000"),
                    storedQuestionHash)));
    given(qnaProjectionPersistencePort.findAnswerByAnswerIdForUpdate(201L))
        .willReturn(
            Optional.of(
                QnaAnswerProjection.create(
                    201L,
                    101L,
                    QnaEscrowIdCodec.questionId(101L),
                    QnaEscrowIdCodec.answerId(201L),
                    22L,
                    storedAnswerHash)));
    given(buildQnaExecutionDraftPort.build(any()))
        .willReturn(draft(QnaExecutionActionType.QNA_ANSWER_ACCEPT));
    given(submitQnaExecutionDraftPort.submit(any()))
        .willReturn(questionIntent("101", "intent-2", "QNA_ANSWER_ACCEPT"));

    service.prepareAnswerAccept(
        new PrepareAnswerAcceptCommand(101L, 201L, 7L, 22L, "온체인 질문", "온체인 답변", 50L));

    ArgumentCaptor<QnaEscrowExecutionRequest> requestCaptor =
        ArgumentCaptor.forClass(QnaEscrowExecutionRequest.class);
    verify(buildQnaExecutionDraftPort).build(requestCaptor.capture());
    QnaEscrowExecutionRequest request = requestCaptor.getValue();
    assertThat(request.actionType()).isEqualTo(QnaExecutionActionType.QNA_ANSWER_ACCEPT);
    assertThat(request.tokenAddress()).isEqualTo("0x1111111111111111111111111111111111111111");
    assertThat(request.rewardAmountWei()).isEqualTo(new BigInteger("50000000000000000000"));
    assertThat(request.questionHash()).isEqualTo(storedQuestionHash);
    assertThat(request.contentHash()).isEqualTo(storedAnswerHash);

    verify(qnaProjectionPersistencePort, never()).saveQuestion(any());
    verify(qnaProjectionPersistencePort, never()).saveAnswer(any());
  }

  @Test
  @DisplayName(
      "prepareAnswerAccept blocks when local question or answer content diverges from projection")
  void prepareAnswerAccept_blocksWhenLocalProjectionHashesDiffer() {
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(Optional.of(questionProjection("온체인 질문", 0)));
    given(qnaProjectionPersistencePort.findAnswerByAnswerIdForUpdate(201L))
        .willReturn(Optional.of(answerProjection("온체인 답변")));

    assertThatThrownBy(
            () ->
                service.prepareAnswerAccept(
                    new PrepareAnswerAcceptCommand(101L, 201L, 7L, 22L, "로컬 질문 본문", "온체인 답변", 50L)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question content differs");
  }

  private QnaExecutionDraft draft(QnaExecutionActionType actionType) {
    return new QnaExecutionDraft(
        QnaExecutionResourceType.QUESTION,
        "101",
        QnaExecutionResourceStatus.PENDING_EXECUTION,
        actionType,
        7L,
        22L,
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

  private QnaExecutionIntentResult questionIntent(
      String resourceId, String intentId, String actionType) {
    return new QnaExecutionIntentResult(
        new QnaExecutionIntentResult.Resource("QUESTION", resourceId, "PENDING_EXECUTION"),
        actionType,
        new QnaExecutionIntentResult.ExecutionIntent(
            intentId, "AWAITING_SIGNATURE", LocalDateTime.of(2026, 4, 14, 10, 0)),
        new QnaExecutionIntentResult.Execution("EIP7702", 2),
        null,
        false);
  }

  private QnaQuestionProjection questionProjection(String questionContent, int answerCount) {
    return QnaQuestionProjection.create(
            101L,
            7L,
            QnaEscrowIdCodec.questionId(101L),
            "0x1111111111111111111111111111111111111111",
            new BigInteger("50000000000000000000"),
            QnaContentHashFactory.hash(questionContent))
        .syncAnswerCount(answerCount);
  }

  private QnaAnswerProjection answerProjection(String answerContent) {
    return QnaAnswerProjection.create(
        201L,
        101L,
        QnaEscrowIdCodec.questionId(101L),
        QnaEscrowIdCodec.answerId(201L),
        22L,
        QnaContentHashFactory.hash(answerContent));
  }
}
