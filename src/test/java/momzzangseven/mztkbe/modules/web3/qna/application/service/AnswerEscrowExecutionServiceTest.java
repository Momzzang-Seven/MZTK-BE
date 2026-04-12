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
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraft;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaRewardTokenConfigPort;
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
@DisplayName("AnswerEscrowExecutionService unit test")
class AnswerEscrowExecutionServiceTest {

  @Mock private LoadQnaRewardTokenConfigPort loadQnaRewardTokenConfigPort;
  @Mock private QnaProjectionPersistencePort qnaProjectionPersistencePort;
  @Mock private BuildQnaExecutionDraftPort buildQnaExecutionDraftPort;
  @Mock private SubmitQnaExecutionDraftPort submitQnaExecutionDraftPort;

  @InjectMocks private AnswerEscrowExecutionService service;

  @Test
  @DisplayName("prepareAnswerCreate fails when the question is not registered onchain")
  void prepareAnswerCreate_failsWhenQuestionProjectionIsMissing() {
    given(loadQnaRewardTokenConfigPort.loadRewardTokenConfig())
        .willReturn(
            new LoadQnaRewardTokenConfigPort.RewardTokenConfig(
                "0x1111111111111111111111111111111111111111", 18));
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
    given(loadQnaRewardTokenConfigPort.loadRewardTokenConfig())
        .willReturn(
            new LoadQnaRewardTokenConfigPort.RewardTokenConfig(
                "0x1111111111111111111111111111111111111111", 18));
    given(qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(101L))
        .willReturn(Optional.of(questionProjection(storedQuestionHash)));
    given(buildQnaExecutionDraftPort.build(any()))
        .willReturn(draft(QnaExecutionActionType.QNA_ANSWER_SUBMIT));
    given(submitQnaExecutionDraftPort.submit(any()))
        .willReturn(new QnaExecutionIntentResult("intent-3", "EIP7702", 2, null, false));

    service.prepareAnswerCreate(
        new PrepareAnswerCreateCommand(101L, 201L, 22L, 7L, "로컬 질문", 50L, "답변 본문", 1));

    ArgumentCaptor<QnaEscrowExecutionRequest> requestCaptor =
        ArgumentCaptor.forClass(QnaEscrowExecutionRequest.class);
    verify(buildQnaExecutionDraftPort).build(requestCaptor.capture());
    QnaEscrowExecutionRequest request = requestCaptor.getValue();
    assertThat(request.actionType()).isEqualTo(QnaExecutionActionType.QNA_ANSWER_SUBMIT);
    assertThat(request.resourceType()).isEqualTo(QnaExecutionResourceType.ANSWER);
    assertThat(request.resourceId()).isEqualTo("201");
    assertThat(request.questionHash()).isEqualTo(storedQuestionHash);
    assertThat(request.contentHash()).isEqualTo(QnaContentHashFactory.hash("답변 본문"));

    verify(qnaProjectionPersistencePort, never()).saveQuestion(any());
    verify(qnaProjectionPersistencePort, never()).saveAnswer(any());
    verify(qnaProjectionPersistencePort, never()).deleteAnswerByAnswerId(any());
  }

  @Test
  @DisplayName("prepareAnswerUpdate fails when the answer is not registered onchain")
  void prepareAnswerUpdate_failsWhenAnswerProjectionIsMissing() {
    given(loadQnaRewardTokenConfigPort.loadRewardTokenConfig())
        .willReturn(
            new LoadQnaRewardTokenConfigPort.RewardTokenConfig(
                "0x1111111111111111111111111111111111111111", 18));
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
  @DisplayName("prepareAnswerDelete uses stored question hash and does not mutate projections")
  void prepareAnswerDelete_usesStoredQuestionHashWithoutMutation() {
    String storedQuestionHash = QnaContentHashFactory.hash("온체인 질문");
    given(loadQnaRewardTokenConfigPort.loadRewardTokenConfig())
        .willReturn(
            new LoadQnaRewardTokenConfigPort.RewardTokenConfig(
                "0x1111111111111111111111111111111111111111", 18));
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
        .willReturn(new QnaExecutionIntentResult("intent-4", "EIP7702", 2, null, false));

    service.prepareAnswerDelete(
        new PrepareAnswerDeleteCommand(101L, 201L, 22L, 7L, "로컬 질문", 50L, 0));

    ArgumentCaptor<QnaEscrowExecutionRequest> requestCaptor =
        ArgumentCaptor.forClass(QnaEscrowExecutionRequest.class);
    verify(buildQnaExecutionDraftPort).build(requestCaptor.capture());
    QnaEscrowExecutionRequest request = requestCaptor.getValue();
    assertThat(request.actionType()).isEqualTo(QnaExecutionActionType.QNA_ANSWER_DELETE);
    assertThat(request.questionHash()).isEqualTo(storedQuestionHash);
    assertThat(request.questionHash()).isNotEqualTo(QnaContentHashFactory.hash("로컬 질문"));
    assertThat(request.contentHash()).isNull();

    verify(qnaProjectionPersistencePort, never()).saveQuestion(any());
    verify(qnaProjectionPersistencePort, never()).saveAnswer(any());
    verify(qnaProjectionPersistencePort, never()).deleteAnswerByAnswerId(any());
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
}
