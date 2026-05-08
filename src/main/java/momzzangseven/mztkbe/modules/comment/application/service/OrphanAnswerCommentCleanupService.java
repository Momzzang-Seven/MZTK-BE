package momzzangseven.mztkbe.modules.comment.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.comment.application.port.in.RunOrphanAnswerCommentCleanupBatchUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.out.DeleteCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadOrphanAnswerCommentCleanupBatchSizePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reconciles active ANSWER comments whose owning answer row has already been hard-deleted. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrphanAnswerCommentCleanupService
    implements RunOrphanAnswerCommentCleanupBatchUseCase {

  private final DeleteCommentPort deleteCommentPort;
  private final LoadOrphanAnswerCommentCleanupBatchSizePort loadBatchSizePort;

  /** Soft-deletes one batch of active orphan answer comments. */
  @Override
  @Transactional
  public int runBatch() {
    int batchSize = loadBatchSizePort.loadBatchSize();
    validateBatchSize(batchSize);

    int softDeleted = deleteCommentPort.softDeleteActiveOrphanAnswerComments(batchSize);
    if (softDeleted > 0) {
      log.info("Orphan answer comment cleanup batch: softDeleted={}", softDeleted);
    }
    return softDeleted;
  }

  private void validateBatchSize(int batchSize) {
    if (batchSize <= 0) {
      throw new IllegalStateException("comment.answer-orphan-cleanup.batch-size must be positive");
    }
  }
}
