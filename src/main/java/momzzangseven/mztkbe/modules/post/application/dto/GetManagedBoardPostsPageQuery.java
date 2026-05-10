package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.modules.post.domain.model.PostModerationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

/** Paged query for admin board post list reads. */
public record GetManagedBoardPostsPageQuery(
    String search,
    PostStatus status,
    PostType type,
    PostPublicationStatus publicationStatus,
    PostModerationStatus moderationStatus,
    int page,
    int size,
    String sortKey) {

  /** Validates page, size, and sort key constraints. */
  public GetManagedBoardPostsPageQuery {
    if (page < 0) {
      throw new IllegalArgumentException("page must be zero or positive");
    }
    if (size <= 0) {
      throw new IllegalArgumentException("size must be positive");
    }
    if (sortKey == null || sortKey.isBlank()) {
      throw new IllegalArgumentException("sortKey is required");
    }
  }
}
