package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.modules.post.domain.model.PostModerationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

/** Query for admin board post list reads. */
public record GetManagedBoardPostsQuery(
    String search,
    Long postId,
    Long userId,
    PostStatus status,
    PostType type,
    PostPublicationStatus publicationStatus,
    PostModerationStatus moderationStatus) {

  public GetManagedBoardPostsQuery {
    search = normalizeSearch(search);
    if (postId != null && postId <= 0) {
      throw new IllegalArgumentException("postId must be positive");
    }
    if (userId != null && userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
  }

  private static String normalizeSearch(String search) {
    if (search == null) {
      return null;
    }
    String trimmed = search.trim();
    return trimmed.isBlank() ? null : trimmed;
  }
}
