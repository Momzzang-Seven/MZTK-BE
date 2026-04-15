package momzzangseven.mztkbe.modules.answer.application.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.answer.application.port.out.DeleteAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.event.AnswerDeletedEvent;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfirmAnswerDeleteSyncService unit test")
class ConfirmAnswerDeleteSyncServiceTest {

  @Mock private LoadAnswerPort loadAnswerPort;
  @Mock private DeleteAnswerPort deleteAnswerPort;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private ConfirmAnswerDeleteSyncService confirmAnswerDeleteSyncService;

  @Test
  @DisplayName("confirmed answer delete removes the local row and publishes the domain event")
  void confirmDeleted_removesAnswerAndPublishesEvent() {
    when(loadAnswerPort.loadAnswerForUpdate(201L)).thenReturn(Optional.of(answer(201L)));

    confirmAnswerDeleteSyncService.confirmDeleted(201L);

    verify(deleteAnswerPort).deleteAnswer(201L);
    verify(eventPublisher).publishEvent(new AnswerDeletedEvent(201L));
  }

  @Test
  @DisplayName("missing local answer row is ignored for idempotent confirmation")
  void confirmDeleted_ignoresMissingAnswer() {
    when(loadAnswerPort.loadAnswerForUpdate(201L)).thenReturn(Optional.empty());

    confirmAnswerDeleteSyncService.confirmDeleted(201L);

    verify(deleteAnswerPort, never()).deleteAnswer(201L);
    verifyNoInteractions(eventPublisher);
  }

  private Answer answer(Long answerId) {
    return Answer.builder()
        .id(answerId)
        .postId(101L)
        .userId(7L)
        .content("답변 내용")
        .isAccepted(false)
        .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
        .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
        .build();
  }
}
