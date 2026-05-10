package momzzangseven.mztkbe.modules.admin.board.application.dto;

import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostModerationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostPublicationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostType;

/** Command for admin board post list reads. */
public record GetAdminBoardPostsCommand(
    Long operatorUserId,
    String search,
    Long postId,
    Long userId,
    AdminBoardPostStatus status,
    AdminBoardPostType type,
    AdminBoardPostPublicationStatus publicationStatus,
    AdminBoardPostModerationStatus moderationStatus,
    int page,
    int size,
    AdminBoardPostSortKey sortKey) {

  public GetAdminBoardPostsCommand {
    if (operatorUserId == null || operatorUserId <= 0) {
      throw new IllegalArgumentException("operatorUserId must be positive");
    }
    if (postId != null && postId <= 0) {
      throw new IllegalArgumentException("postId must be positive");
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
