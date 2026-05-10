package momzzangseven.mztkbe.modules.comment.application.dto;

/** Query for admin board global comment search reads. */
public record GetManagedBoardCommentsQuery(
    String search,
    Long commentId,
    Long userId,
    ManagedBoardCommentTargetType targetType,
    int page,
    int size,
    String sortKey) {

  public GetManagedBoardCommentsQuery {
    if (commentId != null && commentId <= 0) {
      throw new IllegalArgumentException("commentId must be positive");
    }
    if (userId != null && userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
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
