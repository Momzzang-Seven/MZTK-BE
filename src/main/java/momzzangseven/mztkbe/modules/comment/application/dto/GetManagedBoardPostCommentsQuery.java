package momzzangseven.mztkbe.modules.comment.application.dto;

/** Query for admin board comments under a post. */
public record GetManagedBoardPostCommentsQuery(Long postId, int page, int size) {

  public void validate() {
    if (postId == null || postId <= 0) {
      throw new IllegalArgumentException("postId must be positive");
    }
    if (page < 0) {
      throw new IllegalArgumentException("page must be zero or positive");
    }
    if (size <= 0) {
      throw new IllegalArgumentException("size must be positive");
    }
  }
}
