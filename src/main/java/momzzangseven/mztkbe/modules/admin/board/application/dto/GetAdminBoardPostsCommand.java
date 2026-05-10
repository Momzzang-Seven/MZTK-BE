package momzzangseven.mztkbe.modules.admin.board.application.dto;

import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostModerationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostPublicationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostType;

/** Command for admin board post list reads. */
public record GetAdminBoardPostsCommand(
    Long operatorUserId,
    String search,
    AdminBoardPostStatus status,
    AdminBoardPostType type,
    AdminBoardPostPublicationStatus publicationStatus,
    AdminBoardPostModerationStatus moderationStatus,
    int page,
    int size,
    AdminBoardPostSortKey sortKey) {}
