package momzzangseven.mztkbe.modules.admin.board.application.dto;

import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;

/** Command for admin board post list reads. */
public record GetAdminBoardPostsCommand(
    Long operatorUserId,
    String search,
    PostStatus status,
    int page,
    int size,
    AdminBoardPostSortKey sortKey) {}
