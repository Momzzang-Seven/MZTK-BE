package momzzangseven.mztkbe.modules.admin.board.api.dto;

import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardPagePolicies;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardPostSortKey;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostsCommand;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostStatus;
import momzzangseven.mztkbe.modules.admin.common.application.dto.AdminPageQuery;
import momzzangseven.mztkbe.modules.admin.common.application.dto.AdminPageQueryNormalizer;

/** Query DTO for {@code GET /admin/boards/posts}. */
public record GetAdminBoardPostsRequestDTO(
    String search, AdminBoardPostStatus status, Integer page, Integer size, String sort) {

  public GetAdminBoardPostsCommand toCommand(Long operatorUserId) {
    AdminPageQuery pageQuery =
        AdminPageQueryNormalizer.normalize(page, size, search, sort, AdminBoardPagePolicies.POSTS);
    return new GetAdminBoardPostsCommand(
        operatorUserId,
        pageQuery.search(),
        status,
        pageQuery.page(),
        pageQuery.size(),
        AdminBoardPostSortKey.from(pageQuery.sort()));
  }
}
