package momzzangseven.mztkbe.modules.comment.api.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.comment.application.port.in.DeleteCommentUseCase;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommentEventHandler {
  private final DeleteCommentUseCase deleteCommentUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handlePostDeletedEvent(PostDeletedEvent event) {
    try {
      deleteCommentUseCase.deleteCommentsByPostId(event.postId());
      log.debug("Successfully soft-deleted comments for deleted post: postId={}", event.postId());
    } catch (Exception e) {
      log.error(
          "Failed to soft-delete comments for deleted post {}: {}",
          event.postId(),
          e.getMessage(),
          e);
    }
  }
}
