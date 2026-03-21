package momzzangseven.mztkbe.modules.image.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.image.application.dto.UnlinkImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.UnlinkImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Infrastructure-layer event handler that listens for {@link PostDeletedEvent} and delegates image
 * unlinking to {@link UnlinkImagesByReferenceUseCase}.
 *
 * <p>Why AFTER_COMMIT: if the post transaction is rolled back, this handler must not run.
 * AFTER_COMMIT guarantees the post row is durably committed before image cleanup begins.
 *
 * <p>Why REQUIRES_NEW: AFTER_COMMIT fires after the original transaction has already closed, so a
 * new transaction is needed to execute the unlink UPDATE.
 *
 * <p>Failure handling: if unlinking fails, images remain linked with referenceId=postId. The {@code
 * ImageUnlinkedCleanupScheduler} filters by referenceId IS NULL, so these orphaned images will NOT
 * be auto-collected. Monitor the ERROR log and investigate manually.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostDeletedEventHandler {
  private final UnlinkImagesByReferenceUseCase unlinkImagesByReferenceUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(PostDeletedEvent event) {
    ImageReferenceType refType = resolveReferenceType(event.postType());
    UnlinkImagesByReferenceCommand command =
        new UnlinkImagesByReferenceCommand(refType, event.postId());
    try {
      unlinkImagesByReferenceUseCase.execute(command);
      log.debug("Successfully unlinked images for deleted post: postId={}", event.postId());
    } catch (Exception e) {
      log.error(
          "Failed to unlink images for deleted post {}: {}", event.postId(), e.getMessage(), e);
    }
  }

  /**
   * Maps a {@link PostType} to the corresponding {@link ImageReferenceType} stored in the images
   * table.
   *
   * @param postType the type of the deleted post
   * @return the matching image reference type
   */
  private ImageReferenceType resolveReferenceType(PostType postType) {
    return switch (postType) {
      case FREE -> ImageReferenceType.COMMUNITY_FREE;
      case QUESTION -> ImageReferenceType.COMMUNITY_QUESTION;
    };
  }
}
