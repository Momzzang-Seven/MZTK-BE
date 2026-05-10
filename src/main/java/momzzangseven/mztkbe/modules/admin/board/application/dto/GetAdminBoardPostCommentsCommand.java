package momzzangseven.mztkbe.modules.admin.board.application.dto;

/** Command for admin board post comment reads. */
public record GetAdminBoardPostCommentsCommand(
    Long operatorUserId, Long postId, int page, int size) {

  public void validate() {
    if (operatorUserId == null || operatorUserId <= 0) {
      throw new IllegalArgumentException("operatorUserId must be positive");
    }
    if (postId == null || postId <= 0) {
      throw new IllegalArgumentException("postId must be positive");
    }
  }
}
