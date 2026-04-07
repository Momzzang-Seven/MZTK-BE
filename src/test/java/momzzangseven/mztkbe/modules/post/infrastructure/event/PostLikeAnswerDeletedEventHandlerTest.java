package momzzangseven.mztkbe.modules.post.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.answer.domain.event.AnswerDeletedEvent;
import momzzangseven.mztkbe.modules.post.application.port.in.DeleteAnswerLikesUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostLikeAnswerDeletedEventHandler unit test")
class PostLikeAnswerDeletedEventHandlerTest {

  @Mock private DeleteAnswerLikesUseCase deleteAnswerLikesUseCase;

  @InjectMocks private PostLikeAnswerDeletedEventHandler handler;

  @Test
  @DisplayName("deletes answer likes for deleted answer")
  void handle_deletesAnswerLikes() {
    handler.handle(new AnswerDeletedEvent(20L));

    verify(deleteAnswerLikesUseCase).deleteAnswerLikes(20L);
  }

  @Test
  @DisplayName("swallows exception because answer like cleanup runs after commit")
  void handle_swallowsException() {
    doThrow(new RuntimeException("db fail")).when(deleteAnswerLikesUseCase).deleteAnswerLikes(20L);

    assertThatCode(() -> handler.handle(new AnswerDeletedEvent(20L))).doesNotThrowAnyException();
    verify(deleteAnswerLikesUseCase).deleteAnswerLikes(20L);
  }

  @Test
  @DisplayName("is configured to run after commit in a new transaction")
  void handle_hasAfterCommitRequiresNewAnnotations() throws Exception {
    var method =
        PostLikeAnswerDeletedEventHandler.class.getDeclaredMethod(
            "handle", AnswerDeletedEvent.class);

    TransactionalEventListener eventListener =
        method.getAnnotation(TransactionalEventListener.class);
    Transactional transactional = method.getAnnotation(Transactional.class);

    assertThat(eventListener).isNotNull();
    assertThat(eventListener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    assertThat(transactional).isNotNull();
    assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
  }
}
