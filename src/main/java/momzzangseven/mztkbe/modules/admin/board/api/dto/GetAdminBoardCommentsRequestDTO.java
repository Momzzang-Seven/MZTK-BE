package momzzangseven.mztkbe.modules.admin.board.api.dto;

import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardCommentSortKey;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardPagePolicies;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardCommentsCommand;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardCommentTargetType;
import momzzangseven.mztkbe.modules.admin.common.application.dto.AdminPageQuery;
import momzzangseven.mztkbe.modules.admin.common.application.dto.AdminPageQueryNormalizer;

/** Query DTO for {@code GET /admin/boards/comments}. */
public record GetAdminBoardCommentsRequestDTO(
    String search,
    Long commentId,
    Long userId,
    AdminBoardCommentTargetType targetType,
    Integer page,
    Integer size,
    String sort) {

  public GetAdminBoardCommentsCommand toCommand(Long operatorUserId) {
    AdminPageQuery pageQuery =
        AdminPageQueryNormalizer.normalize(
            page, size, search, sort, AdminBoardPagePolicies.COMMENTS);
    return new GetAdminBoardCommentsCommand(
        operatorUserId,
        pageQuery.search(),
        commentId,
        userId,
        targetType,
        pageQuery.page(),
        pageQuery.size(),
        AdminBoardCommentSortKey.from(pageQuery.sort()));
  }
}
