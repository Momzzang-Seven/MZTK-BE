package momzzangseven.mztkbe.modules.admin.board.application.port.out;

/** Output port for admin board post list policy values. */
public interface LoadAdminBoardPostListPolicyPort {

  /** Maximum matching posts that COMMENT_COUNT sort may scan in memory. */
  int maxCommentCountSortScanSize();
}
