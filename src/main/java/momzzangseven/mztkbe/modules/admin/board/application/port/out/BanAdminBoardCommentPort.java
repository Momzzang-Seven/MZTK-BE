package momzzangseven.mztkbe.modules.admin.board.application.port.out;

public interface BanAdminBoardCommentPort {

  BanAdminBoardCommentResult ban(Long commentId);

  record BanAdminBoardCommentResult(Long commentId, Long postId, boolean moderated) {}
}
