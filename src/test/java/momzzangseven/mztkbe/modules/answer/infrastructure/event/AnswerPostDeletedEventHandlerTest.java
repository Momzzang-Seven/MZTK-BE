package momzzangseven.mztkbe.modules.answer.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.answer.application.port.in.DeleteAnswersByPostUseCase;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerPostDeletedEventHandler unit test")
class AnswerPostDeletedEventHandlerTest {

  @Mock private DeleteAnswersByPostUseCase deleteAnswersByPostUseCase;

  @InjectMocks private AnswerPostDeletedEventHandler handler;

  @Nested
  @DisplayName("handle(PostDeletedEvent)")
  class Handle {

    @Test
    @DisplayName("calls deleteAnswersByPostUseCase with postId")
    void handle_callsDeleteAnswersByPostUseCase() {
      PostDeletedEvent event = new PostDeletedEvent(10L, PostType.QUESTION);

      handler.handle(event);

      verify(deleteAnswersByPostUseCase).deleteByPostId(10L);
    }

    @Test
    @DisplayName("logs exception and does not throw")
    void handle_logsExceptionAndDoesNotThrow() {
      PostDeletedEvent event = new PostDeletedEvent(20L, PostType.QUESTION);
      doThrow(new RuntimeException("db fail")).when(deleteAnswersByPostUseCase).deleteByPostId(20L);

      assertThatCode(() -> handler.handle(event)).doesNotThrowAnyException();
      verify(deleteAnswersByPostUseCase).deleteByPostId(20L);
    }
  }
}
