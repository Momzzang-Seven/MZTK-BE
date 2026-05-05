package momzzangseven.mztkbe.modules.comment.application.dto;

/** Command for admin-managed comment soft delete. */
public record BanManagedCommentCommand(Long commentId) {

  public void validate() {
    if (commentId == null || commentId <= 0) {
      throw new IllegalArgumentException("commentId must be positive");
    }
  }
}
