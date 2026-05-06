package momzzangseven.mztkbe.modules.admin.board.application.dto;

import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostStatus;

/** Command for admin board post list reads. */
public record GetAdminBoardPostsCommand(
    Long operatorUserId,
    String search,
    AdminBoardPostStatus status,
    int page,
    int size,
    AdminBoardPostSortKey sortKey) {}
