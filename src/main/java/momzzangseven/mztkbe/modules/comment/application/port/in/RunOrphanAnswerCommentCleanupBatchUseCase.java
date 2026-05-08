package momzzangseven.mztkbe.modules.comment.application.port.in;

/** Runs one batch of active orphan answer comment cleanup. */
public interface RunOrphanAnswerCommentCleanupBatchUseCase {

  /**
   * Soft-deletes one configured-size batch of active answer comments whose answer no longer exists.
   *
   * @return number of comments soft-deleted in this batch
   */
  int runBatch();
}
