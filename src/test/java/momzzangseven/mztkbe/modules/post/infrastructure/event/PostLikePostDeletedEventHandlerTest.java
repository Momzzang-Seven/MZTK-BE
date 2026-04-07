package momzzangseven.mztkbe.modules.post.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
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
@DisplayName("PostLikePostDeletedEventHandler unit test")
class PostLikePostDeletedEventHandlerTest {

  @Mock private PostLikePersistencePort postLikePersistencePort;

  @InjectMocks private PostLikePostDeletedEventHandler handler;

  @Test
  @DisplayName("deletes post likes for deleted post")
  void handle_deletesPostLikes() {
    handler.handle(new PostDeletedEvent(10L, PostType.FREE));

    verify(postLikePersistencePort).deleteByTarget(PostLikeTargetType.POST, 10L);
  }

  @Test
  @DisplayName("swallows exception because post like cleanup runs after commit")
  void handle_swallowsException() {
    doThrow(new RuntimeException("db fail"))
        .when(postLikePersistencePort)
        .deleteByTarget(PostLikeTargetType.POST, 10L);

    assertThatCode(() -> handler.handle(new PostDeletedEvent(10L, PostType.FREE)))
        .doesNotThrowAnyException();
    verify(postLikePersistencePort).deleteByTarget(PostLikeTargetType.POST, 10L);
  }

  @Test
  @DisplayName("is configured to run after commit in a new transaction")
  void handle_hasAfterCommitRequiresNewAnnotations() throws Exception {
    var method =
        PostLikePostDeletedEventHandler.class.getDeclaredMethod("handle", PostDeletedEvent.class);

    TransactionalEventListener eventListener =
        method.getAnnotation(TransactionalEventListener.class);
    Transactional transactional = method.getAnnotation(Transactional.class);

    assertThat(eventListener).isNotNull();
    assertThat(eventListener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    assertThat(transactional).isNotNull();
    assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
  }
}
