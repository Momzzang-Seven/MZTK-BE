package momzzangseven.mztkbe.modules.comment.api.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.comment.application.port.in.DeleteCommentUseCase;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentEventHandler unit test")
class CommentEventHandlerTest {

  @Mock private DeleteCommentUseCase deleteCommentUseCase;

  @InjectMocks private CommentEventHandler commentEventHandler;

  @Test
  @DisplayName("handlePostDeletedEvent() calls deleteCommentsByPostId() with event postId")
  void handlePostDeletedEvent_callsDeleteCommentsByPostId() {
    PostDeletedEvent event = new PostDeletedEvent(100L, PostType.QUESTION);

    commentEventHandler.handlePostDeletedEvent(event);

    verify(deleteCommentUseCase).deleteCommentsByPostId(100L);
  }

  @Test
  @DisplayName("handlePostDeletedEvent() swallows exception because comment cleanup is post-commit")
  void handlePostDeletedEvent_swallowsException() {
    PostDeletedEvent event = new PostDeletedEvent(200L, PostType.FREE);
    doThrow(new RuntimeException("db fail"))
        .when(deleteCommentUseCase)
        .deleteCommentsByPostId(200L);

    assertThatCode(() -> commentEventHandler.handlePostDeletedEvent(event))
        .doesNotThrowAnyException();
    verify(deleteCommentUseCase).deleteCommentsByPostId(200L);
  }
}
