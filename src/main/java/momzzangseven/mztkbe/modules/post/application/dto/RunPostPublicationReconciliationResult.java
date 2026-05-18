package momzzangseven.mztkbe.modules.post.application.dto;

import java.util.List;

public record RunPostPublicationReconciliationResult(
    int scannedCount,
    int unchangedCount,
    int changedToPendingCount,
    int changedToVisibleCount,
    int changedToFailedCount,
    int needsReviewCount,
    int staleSkippedCount,
    Long lastScannedPostId,
    boolean dryRun,
    List<Long> needsReviewPostIds,
    List<Long> staleSkippedPostIds) {

  public RunPostPublicationReconciliationResult {
    needsReviewPostIds = needsReviewPostIds == null ? List.of() : List.copyOf(needsReviewPostIds);
    staleSkippedPostIds =
        staleSkippedPostIds == null ? List.of() : List.copyOf(staleSkippedPostIds);
  }
}
