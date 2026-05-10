package momzzangseven.mztkbe.modules.admin.board.application.dto;

import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardCommentTargetType;

/** Command for admin board global comment search reads. */
public record GetAdminBoardCommentsCommand(
    Long operatorUserId,
    String search,
    Long commentId,
    Long userId,
    AdminBoardCommentTargetType targetType,
    int page,
    int size,
    AdminBoardCommentSortKey sortKey) {

  public GetAdminBoardCommentsCommand {
    if (operatorUserId == null || operatorUserId <= 0) {
      throw new IllegalArgumentException("operatorUserId must be positive");
    }
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
    if (sortKey == null) {
      throw new IllegalArgumentException("sortKey is required");
    }
  }
}
