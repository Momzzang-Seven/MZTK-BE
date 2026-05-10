package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;

/** Query for admin board post list reads. */
public record GetManagedBoardPostsQuery(
    String search, Long postId, Long userId, PostStatus status) {

  public GetManagedBoardPostsQuery {
    if (postId != null && postId <= 0) {
      throw new IllegalArgumentException("postId must be positive");
    }
    if (userId != null && userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
  }
}
