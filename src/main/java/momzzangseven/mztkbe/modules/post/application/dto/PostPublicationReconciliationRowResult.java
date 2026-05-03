package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;

public record PostPublicationReconciliationRowResult(
    Long postId, Outcome outcome, PostPublicationStatus targetStatus) {

  public enum Outcome {
    UNCHANGED,
    CHANGED,
    NEEDS_REVIEW,
    STALE_SKIPPED
  }

  public static PostPublicationReconciliationRowResult unchanged(Long postId) {
    return new PostPublicationReconciliationRowResult(postId, Outcome.UNCHANGED, null);
  }

  public static PostPublicationReconciliationRowResult changed(
      Long postId, PostPublicationStatus targetStatus) {
    return new PostPublicationReconciliationRowResult(postId, Outcome.CHANGED, targetStatus);
  }

  public static PostPublicationReconciliationRowResult needsReview(Long postId) {
    return new PostPublicationReconciliationRowResult(postId, Outcome.NEEDS_REVIEW, null);
  }

  public static PostPublicationReconciliationRowResult staleSkipped(Long postId) {
    return new PostPublicationReconciliationRowResult(postId, Outcome.STALE_SKIPPED, null);
  }
}
