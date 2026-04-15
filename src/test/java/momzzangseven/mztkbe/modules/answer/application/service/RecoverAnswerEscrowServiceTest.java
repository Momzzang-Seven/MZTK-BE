package momzzangseven.mztkbe.modules.answer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.answer.AnswerUnsupportedPostTypeException;
import momzzangseven.mztkbe.global.error.answer.CannotAnswerSolvedPostException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerMutationResult;
import momzzangseven.mztkbe.modules.answer.application.dto.RecoverAnswerEscrowCommand;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
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

  @InjectMocks private RecoverAnswerEscrowService service;

  @Test
  @DisplayName("recoverAnswerCreate blocks when the parent post is not a question")
  void recoverAnswerCreate_blocksWhenPostIsNotQuestion() {
    RecoverAnswerEscrowCommand command = new RecoverAnswerEscrowCommand(20L, 10L, 99L);
    given(loadAnswerPort.loadAnswerForUpdate(99L)).willReturn(Optional.of(answer(99L, 10L, 20L)));
    given(loadPostPort.loadPost(10L))
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
    given(loadPostPort.loadPost(10L))
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
    given(loadPostPort.loadPost(10L))
        .willReturn(
            Optional.of(new LoadPostPort.PostContext(10L, 30L, false, true, "질문 본문", 50L, false)));
    given(countAnswersPort.countAnswers(10L)).willReturn(1L);
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
    given(loadPostPort.loadPost(10L))
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
    return Answer.builder()
        .id(answerId)
        .postId(postId)
        .userId(userId)
        .content("답변 본문")
        .isAccepted(false)
        .build();
  }
}
