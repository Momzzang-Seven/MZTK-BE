package momzzangseven.mztkbe.modules.answer.application.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.answer.application.dto.SyncAnswerUpdateStateCommand;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateImagePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateStatePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SyncAnswerUpdateStateService unit test")
class SyncAnswerUpdateStateServiceTest {

  @Mock private AnswerUpdateStatePort answerUpdateStatePort;
  @Mock private AnswerUpdateImagePort answerUpdateImagePort;
  @Mock private LoadAnswerPort loadAnswerPort;
  @Mock private SaveAnswerPort saveAnswerPort;

  @InjectMocks private SyncAnswerUpdateStateService service;

  @Test
  @DisplayName("failAnswerUpdate marks the current update state failed")
  void failAnswerUpdate_marksCurrentStateFailed() {
    SyncAnswerUpdateStateCommand command =
        new SyncAnswerUpdateStateCommand(
            100L, 3L, "update-token", "intent-update", "RPC_UNAVAILABLE");

    service.failAnswerUpdate(command);

    verify(answerUpdateStatePort)
        .markFailedIfCurrent(100L, 3L, "update-token", "intent-update", "RPC_UNAVAILABLE");
  }

  @Test
  @DisplayName("confirmAnswerUpdate applies pending content and pending images atomically")
  void confirmAnswerUpdate_appliesPendingContentAndImages() {
    SyncAnswerUpdateStateCommand command =
        new SyncAnswerUpdateStateCommand(100L, 3L, "update-token", "intent-update", null);
    AnswerUpdateStatePort.AnswerUpdateState state =
        new AnswerUpdateStatePort.AnswerUpdateState(
            500L, 100L, 3L, "update-token", "intent-update", "updated", true);
    Answer answer =
        Answer.builder()
            .id(100L)
            .postId(10L)
            .userId(20L)
            .content("before")
            .isAccepted(false)
            .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
            .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .build();

    when(answerUpdateStatePort.loadIntentBoundState(100L, 3L, "update-token", "intent-update"))
        .thenReturn(Optional.of(state));
    when(loadAnswerPort.loadAnswerForUpdate(100L)).thenReturn(Optional.of(answer));

    service.confirmAnswerUpdate(command);

    ArgumentCaptor<Answer> answerCaptor = ArgumentCaptor.forClass(Answer.class);
    verify(saveAnswerPort).saveAnswer(answerCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(answerCaptor.getValue().getContent())
        .isEqualTo("updated");
    verify(answerUpdateImagePort).applyPendingImages(500L, 20L, 100L);
    verify(answerUpdateStatePort).markConfirmed(500L);
  }
}
