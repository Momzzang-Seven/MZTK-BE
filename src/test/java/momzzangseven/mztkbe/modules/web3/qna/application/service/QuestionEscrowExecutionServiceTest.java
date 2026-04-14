package momzzangseven.mztkbe.modules.web3.qna.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerAcceptCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraft;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaRewardTokenConfigPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.PrecheckQuestionFundingPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.SubmitQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaAnswerProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
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
  @Mock private BuildQnaExecutionDraftPort buildQnaExecutionDraftPort;
  @Mock private SubmitQnaExecutionDraftPort submitQnaExecutionDraftPort;

  @InjectMocks private QuestionEscrowExecutionService service;

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
        .willReturn(new QnaExecutionIntentResult("intent-1", "EIP7702", 2, null, false));

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
    assertThat(result.executionIntentId()).isEqualTo("intent-1");
  }

  @Test
  @DisplayName("prepareQuestionUpdate fails when the question is not registered onchain")
  void prepareQuestionUpdate_failsWhenProjectionIsMissing() {
    given(loadQnaRewardTokenConfigPort.loadRewardTokenConfig())
        .willReturn(
            new LoadQnaRewardTokenConfigPort.RewardTokenConfig(
                "0x1111111111111111111111111111111111111111", 18));
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
  @DisplayName("prepareAnswerAccept uses stored projection hashes and does not mutate projection")
  void prepareAnswerAccept_usesStoredProjectionHashes() {
    String storedQuestionHash = QnaContentHashFactory.hash("온체인 질문");
    String storedAnswerHash = QnaContentHashFactory.hash("온체인 답변");

    given(loadQnaRewardTokenConfigPort.loadRewardTokenConfig())
        .willReturn(
            new LoadQnaRewardTokenConfigPort.RewardTokenConfig(
                "0x1111111111111111111111111111111111111111", 18));
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
        .willReturn(new QnaExecutionIntentResult("intent-2", "EIP7702", 2, null, false));

    service.prepareAnswerAccept(
        new PrepareAnswerAcceptCommand(101L, 201L, 7L, 22L, "로컬 질문 본문", "로컬 답변 본문", 50L));

    ArgumentCaptor<QnaEscrowExecutionRequest> requestCaptor =
        ArgumentCaptor.forClass(QnaEscrowExecutionRequest.class);
    verify(buildQnaExecutionDraftPort).build(requestCaptor.capture());
    QnaEscrowExecutionRequest request = requestCaptor.getValue();
    assertThat(request.actionType()).isEqualTo(QnaExecutionActionType.QNA_ANSWER_ACCEPT);
    assertThat(request.questionHash()).isEqualTo(storedQuestionHash);
    assertThat(request.contentHash()).isEqualTo(storedAnswerHash);
    assertThat(request.questionHash()).isNotEqualTo(QnaContentHashFactory.hash("로컬 질문 본문"));
    assertThat(request.contentHash()).isNotEqualTo(QnaContentHashFactory.hash("로컬 답변 본문"));

    verify(qnaProjectionPersistencePort, never()).saveQuestion(any());
    verify(qnaProjectionPersistencePort, never()).saveAnswer(any());
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
}
