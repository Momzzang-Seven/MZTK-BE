package momzzangseven.mztkbe.modules.post.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.post.application.port.in.DeletePostLikesUseCase;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostLikePostDeletedEventHandler {

  private final DeletePostLikesUseCase deletePostLikesUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(PostDeletedEvent event) {
    try {
      deletePostLikesUseCase.deletePostLikes(event.postId());
      log.debug("Successfully deleted post likes for deleted post: postId={}", event.postId());
    } catch (Exception e) {
      log.error(
          "Failed to delete post likes for deleted post {}: {}", event.postId(), e.getMessage(), e);
    }
  }
}
