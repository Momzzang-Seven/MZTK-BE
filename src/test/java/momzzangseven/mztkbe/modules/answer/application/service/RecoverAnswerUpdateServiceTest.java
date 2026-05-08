package momzzangseven.mztkbe.modules.answer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.answer.AnswerPublicationStateException;
import momzzangseven.mztkbe.modules.answer.application.dto.RecoverAnswerUpdateCommand;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerExecutionWriteView;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateStatePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerUpdateStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecoverAnswerUpdateService")
class RecoverAnswerUpdateServiceTest {

  @Mock private LoadAnswerPort loadAnswerPort;
  @Mock private LoadPostPort loadPostPort;
  @Mock private CountAnswersPort countAnswersPort;
  @Mock private AnswerUpdateStatePort answerUpdateStatePort;
  @Mock private AnswerLifecycleExecutionPort answerLifecycleExecutionPort;

  @InjectMocks private RecoverAnswerUpdateService service;

  @Test
  @DisplayName("recoverAnswerUpdate prepares web3 update and binds it to the recoverable state")
  void recoverAnswerUpdateBindsPreparedIntent() {
    RecoverAnswerUpdateCommand command = new RecoverAnswerUpdateCommand(20L, 10L, 100L);
    AnswerUpdateStatePort.AnswerUpdateState state = updateState();
    given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.of(answer()));
    given(answerUpdateStatePort.loadLatestRecoverable(100L)).willReturn(Optional.of(state));
    given(loadPostPort.loadPost(10L))
        .willReturn(
            Optional.of(
                new LoadPostPort.PostContext(10L, 30L, false, true, "question", 50L, false)));
    given(countAnswersPort.countAnswers(10L)).willReturn(2L);
    given(
            answerLifecycleExecutionPort.prepareAnswerUpdate(
                10L, 100L, 20L, 30L, "question", 50L, "updated", 2, 3L, "update-token"))
        .willReturn(Optional.of(answerWeb3("intent-update")));
    given(answerUpdateStatePort.bindRecoveryIntentIfCurrent(500L, "intent-update")).willReturn(1);

    var result = service.recoverAnswerUpdate(command);

    assertThat(result.pendingUpdateStatus()).isEqualTo(AnswerUpdateStatus.INTENT_BOUND);
    assertThat(result.pendingUpdateVersion()).isEqualTo(3L);
    assertThat(result.web3().executionIntent().id()).isEqualTo("intent-update");
  }

  @Test
  @DisplayName("recoverAnswerUpdate cancels the prepared intent when bind lost the race")
  void recoverAnswerUpdateCancelsIntentWhenBindLostRace() {
    RecoverAnswerUpdateCommand command = new RecoverAnswerUpdateCommand(20L, 10L, 100L);
    AnswerUpdateStatePort.AnswerUpdateState state = updateState();
    given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.of(answer()));
    given(answerUpdateStatePort.loadLatestRecoverable(100L)).willReturn(Optional.of(state));
    given(loadPostPort.loadPost(10L))
        .willReturn(
            Optional.of(
                new LoadPostPort.PostContext(10L, 30L, false, true, "question", 50L, false)));
    given(countAnswersPort.countAnswers(10L)).willReturn(2L);
    given(
            answerLifecycleExecutionPort.prepareAnswerUpdate(
                10L, 100L, 20L, 30L, "question", 50L, "updated", 2, 3L, "update-token"))
        .willReturn(Optional.of(answerWeb3("intent-update")));
    given(answerUpdateStatePort.bindRecoveryIntentIfCurrent(500L, "intent-update")).willReturn(0);
    given(answerUpdateStatePort.loadIntentBoundState(100L, 3L, "update-token", "intent-update"))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> service.recoverAnswerUpdate(command))
        .isInstanceOf(AnswerPublicationStateException.class);
    verify(answerLifecycleExecutionPort)
        .cancelSignableIntent("intent-update", "answer update recovery intent bind failed");
  }

  @Test
  @DisplayName("recoverAnswerUpdate keeps duplicate prepared intent when it is already bound")
  void recoverAnswerUpdateKeepsAlreadyBoundDuplicateIntent() {
    RecoverAnswerUpdateCommand command = new RecoverAnswerUpdateCommand(20L, 10L, 100L);
    AnswerUpdateStatePort.AnswerUpdateState state = updateState();
    given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.of(answer()));
    given(answerUpdateStatePort.loadLatestRecoverable(100L)).willReturn(Optional.of(state));
    given(loadPostPort.loadPost(10L))
        .willReturn(
            Optional.of(
                new LoadPostPort.PostContext(10L, 30L, false, true, "question", 50L, false)));
    given(countAnswersPort.countAnswers(10L)).willReturn(2L);
    given(
            answerLifecycleExecutionPort.prepareAnswerUpdate(
                10L, 100L, 20L, 30L, "question", 50L, "updated", 2, 3L, "update-token"))
        .willReturn(Optional.of(answerWeb3("intent-update")));
    given(answerUpdateStatePort.bindRecoveryIntentIfCurrent(500L, "intent-update")).willReturn(0);
    given(answerUpdateStatePort.loadIntentBoundState(100L, 3L, "update-token", "intent-update"))
        .willReturn(Optional.of(state));

    var result = service.recoverAnswerUpdate(command);

    assertThat(result.web3().executionIntent().id()).isEqualTo("intent-update");
    verify(answerLifecycleExecutionPort, never())
        .cancelSignableIntent("intent-update", "answer update recovery intent bind failed");
  }

  private Answer answer() {
    return Answer.builder()
        .id(100L)
        .postId(10L)
        .userId(20L)
        .content("answer")
        .isAccepted(false)
        .build();
  }

  private AnswerUpdateStatePort.AnswerUpdateState updateState() {
    return new AnswerUpdateStatePort.AnswerUpdateState(
        500L, 100L, 3L, "update-token", null, "updated", false);
  }

  private AnswerExecutionWriteView answerWeb3(String executionIntentId) {
    return new AnswerExecutionWriteView(
        new AnswerExecutionWriteView.Resource("ANSWER", "100", "PENDING_EXECUTION"),
        "QNA_ANSWER_UPDATE",
        new AnswerExecutionWriteView.ExecutionIntent(executionIntentId, "AWAITING_SIGNATURE", null),
        new AnswerExecutionWriteView.Execution("EIP7702", 2),
        null,
        false);
  }
}
