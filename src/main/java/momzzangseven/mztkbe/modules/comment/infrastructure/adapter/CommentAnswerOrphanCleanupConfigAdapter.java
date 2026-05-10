package momzzangseven.mztkbe.modules.comment.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadOrphanAnswerCommentCleanupBatchSizePort;
import momzzangseven.mztkbe.modules.comment.infrastructure.config.CommentAnswerOrphanCleanupProperties;
import org.springframework.stereotype.Component;

/** Adapts configured orphan answer comment cleanup policy values to the application port. */
@Component
@RequiredArgsConstructor
public class CommentAnswerOrphanCleanupConfigAdapter
    implements LoadOrphanAnswerCommentCleanupBatchSizePort {

  private final CommentAnswerOrphanCleanupProperties properties;

  /** Returns the configured orphan answer comment cleanup batch size. */
  @Override
  public int loadBatchSize() {
    return properties.getBatchSize();
  }
}
