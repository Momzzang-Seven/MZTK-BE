package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;

public record RunPostPublicationReconciliationCommand(
    Long afterPostId, Integer batchSize, boolean dryRun) {

  private static final int DEFAULT_BATCH_SIZE = 100;
  private static final int MAX_BATCH_SIZE = 1_000;

  public int effectiveBatchSize() {
    if (batchSize == null) {
      return DEFAULT_BATCH_SIZE;
    }
    if (batchSize <= 0 || batchSize > MAX_BATCH_SIZE) {
      throw new PostInvalidInputException("batchSize must be between 1 and 1000.");
    }
    return batchSize;
  }
}
