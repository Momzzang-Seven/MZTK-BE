package momzzangseven.mztkbe.modules.post.application.dto;

public record RunPostPublicationReconciliationResult(
    int scannedCount,
    int unchangedCount,
    int changedToPendingCount,
    int changedToVisibleCount,
    int changedToFailedCount,
    int needsReviewCount,
    int staleSkippedCount,
    Long lastScannedPostId,
    boolean dryRun) {}
