package momzzangseven.mztkbe.modules.answer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.answer.AnswerPublicationStateException;
import momzzangseven.mztkbe.global.error.answer.AnswerUnsupportedPostTypeException;
import momzzangseven.mztkbe.global.error.answer.CannotAnswerSolvedPostException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerExecutionWriteView;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerMutationResult;
import momzzangseven.mztkbe.modules.answer.application.dto.RecoverAnswerEscrowCommand;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerLifecycleAction;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerPublicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecoverAnswerEscrowService")
class RecoverAnswerEscrowServiceTest {

  @Mock private LoadAnswerPort loadAnswerPort;
  @Mock private LoadPostPort loadPostPort;
  @Mock private CountAnswersPort countAnswersPort;
  @Mock private AnswerLifecycleExecutionPort answerLifecycleExecutionPort;
  @Mock private SaveAnswerPort saveAnswerPort;

  @InjectMocks private RecoverAnswerEscrowService service;

  @Test
  @DisplayName(
      "recoverAnswerCreate cancels the new intent when another recovery already bound the answer")
  void recoverAnswerCreateCancelsIntentWhenSecondTransactionSeesNonFailedAnswer() {
    RecoverAnswerEscrowCommand command = new RecoverAnswerEscrowCommand(20L, 10L, 99L);
    given(answerLifecycleExecutionPort.managesAnswerLifecycle(AnswerLifecycleAction.CREATE))
        .willReturn(true);
    given(loadAnswerPort.loadAnswerForUpdate(99L))
        .willReturn(
            Optional.of(answer(99L, 10L, 20L, AnswerPublicationStatus.FAILED)),
            Optional.of(answer(99L, 10L, 20L, AnswerPublicationStatus.PENDING)));
    given(loadPostPort.loadPostForUpdate(10L))
        .willReturn(
            Optional.of(new LoadPostPort.PostContext(10L, 30L, false, true, "질문 본문", 50L, false)));
    given(countAnswersPort.countOnchainBlockingAnswers(10L)).willReturn(0L);
    given(
            answerLifecycleExecutionPort.recoverAnswerCreate(
                10L, 99L, 20L, 30L, "질문 본문", 50L, "답변 본문", 1))
        .willReturn(Optional.of(answerWeb3("intent-recover")));

    assertThatThrownBy(() -> service.recoverAnswerCreate(command))
        .isInstanceOf(AnswerPublicationStateException.class);
    verify(saveAnswerPort, never()).saveAnswer(any(Answer.class));
    verify(saveAnswerPort, never()).bindCreateIntentIfCurrent(anyLong(), anyString(), anyString());
    verify(answerLifecycleExecutionPort)
        .cancelSignableIntent("intent-recover", "answer create recovery intent bind failed");
  }

  @Test
  @DisplayName("recoverAnswerCreate blocks when the parent post is not a question")
  void recoverAnswerCreate_blocksWhenPostIsNotQuestion() {
    RecoverAnswerEscrowCommand command = new RecoverAnswerEscrowCommand(20L, 10L, 99L);
    given(loadAnswerPort.loadAnswerForUpdate(99L)).willReturn(Optional.of(answer(99L, 10L, 20L)));
    given(loadPostPort.loadPostForUpdate(10L))
        .willReturn(Optional.of(new LoadPostPort.PostContext(10L, 30L, false, false)));

    assertThatThrownBy(() -> service.recoverAnswerCreate(command))
        .isInstanceOf(AnswerUnsupportedPostTypeException.class);
    verify(answerLifecycleExecutionPort, never()).precheckAnswerCreate(anyLong(), anyString());
    verify(answerLifecycleExecutionPort, never())
        .recoverAnswerCreate(
            anyLong(),
            anyLong(),
            anyLong(),
            anyLong(),
            anyString(),
            anyLong(),
            anyString(),
            anyInt());
  }

  @Test
  @DisplayName("recoverAnswerCreate blocks when the parent question is solved or pending accept")
  void recoverAnswerCreate_blocksWhenQuestionIsLocked() {
    RecoverAnswerEscrowCommand command = new RecoverAnswerEscrowCommand(20L, 10L, 99L);
    given(loadAnswerPort.loadAnswerForUpdate(99L)).willReturn(Optional.of(answer(99L, 10L, 20L)));
    given(loadPostPort.loadPostForUpdate(10L))
        .willReturn(
            Optional.of(new LoadPostPort.PostContext(10L, 30L, false, true, "질문", 50L, true)));

    assertThatThrownBy(() -> service.recoverAnswerCreate(command))
        .isInstanceOf(CannotAnswerSolvedPostException.class);
    verify(answerLifecycleExecutionPort, never()).precheckAnswerCreate(anyLong(), anyString());
    verify(answerLifecycleExecutionPort, never())
        .recoverAnswerCreate(
            anyLong(),
            anyLong(),
            anyLong(),
            anyLong(),
            anyString(),
            anyLong(),
            anyString(),
            anyInt());
  }

  @Test
  @DisplayName("recoverAnswerCreate runs the same web3 precheck as normal create before recovery")
  void recoverAnswerCreate_runsPrecheckBeforeRecovery() {
    RecoverAnswerEscrowCommand command = new RecoverAnswerEscrowCommand(20L, 10L, 99L);
    given(loadAnswerPort.loadAnswerForUpdate(99L)).willReturn(Optional.of(answer(99L, 10L, 20L)));
    given(loadPostPort.loadPostForUpdate(10L))
        .willReturn(
            Optional.of(new LoadPostPort.PostContext(10L, 30L, false, true, "질문 본문", 50L, false)));
    given(countAnswersPort.countOnchainBlockingAnswers(10L)).willReturn(0L);
    given(
            answerLifecycleExecutionPort.recoverAnswerCreate(
                10L, 99L, 20L, 30L, "질문 본문", 50L, "답변 본문", 1))
        .willReturn(Optional.empty());

    AnswerMutationResult result = service.recoverAnswerCreate(command);

    assertThat(result.postId()).isEqualTo(10L);
    assertThat(result.answerId()).isEqualTo(99L);
    assertThat(result.web3()).isNull();
    verify(answerLifecycleExecutionPort).precheckAnswerCreate(10L, "질문 본문");
    verify(answerLifecycleExecutionPort)
        .recoverAnswerCreate(10L, 99L, 20L, 30L, "질문 본문", 50L, "답변 본문", 1);
  }

  @Test
  @DisplayName("recoverAnswerCreate stops when the shared web3 precheck fails")
  void recoverAnswerCreate_stopsWhenPrecheckFails() {
    RecoverAnswerEscrowCommand command = new RecoverAnswerEscrowCommand(20L, 10L, 99L);
    given(loadAnswerPort.loadAnswerForUpdate(99L)).willReturn(Optional.of(answer(99L, 10L, 20L)));
    given(loadPostPort.loadPostForUpdate(10L))
        .willReturn(
            Optional.of(new LoadPostPort.PostContext(10L, 30L, false, true, "질문 본문", 50L, false)));
    willThrow(new Web3InvalidInputException("question has active onchain mutation"))
        .given(answerLifecycleExecutionPort)
        .precheckAnswerCreate(10L, "질문 본문");

    assertThatThrownBy(() -> service.recoverAnswerCreate(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("active onchain mutation");
    verify(answerLifecycleExecutionPort).precheckAnswerCreate(10L, "질문 본문");
    verify(answerLifecycleExecutionPort, never())
        .recoverAnswerCreate(
            anyLong(),
            anyLong(),
            anyLong(),
            anyLong(),
            anyString(),
            anyLong(),
            anyString(),
            anyInt());
  }

  private Answer answer(Long answerId, Long postId, Long userId) {
    return answer(answerId, postId, userId, AnswerPublicationStatus.VISIBLE);
  }

  private Answer answer(
      Long answerId, Long postId, Long userId, AnswerPublicationStatus publicationStatus) {
    return Answer.builder()
        .id(answerId)
        .postId(postId)
        .userId(userId)
        .content("답변 본문")
        .isAccepted(false)
        .publicationStatus(publicationStatus)
        .build();
  }

  private AnswerExecutionWriteView answerWeb3(String executionIntentId) {
    return new AnswerExecutionWriteView(
        new AnswerExecutionWriteView.Resource("ANSWER", "99", "PENDING_EXECUTION"),
        "QNA_ANSWER_SUBMIT",
        new AnswerExecutionWriteView.ExecutionIntent(
            executionIntentId, "AWAITING_SIGNATURE", null, 1L),
        new AnswerExecutionWriteView.Execution("EIP7702", 2),
        null,
        false);
  }
}
