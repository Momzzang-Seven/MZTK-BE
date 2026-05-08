package momzzangseven.mztkbe.modules.comment.application.port.out;

/** Supplies batch policy values for active orphan answer comment cleanup. */
public interface LoadOrphanAnswerCommentCleanupBatchSizePort {

  /**
   * Loads the maximum number of comments to process in one cleanup batch.
   *
   * @return configured batch size
   */
  int loadBatchSize();
}
