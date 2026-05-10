package momzzangseven.mztkbe.modules.admin.board.api.dto;

import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardPagePolicies;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostCommentsCommand;
import momzzangseven.mztkbe.modules.admin.common.application.dto.AdminPageQuery;
import momzzangseven.mztkbe.modules.admin.common.application.dto.AdminPageQueryNormalizer;

/** Query DTO for {@code GET /admin/boards/posts/{postId}/comments}. */
public record GetAdminBoardPostCommentsRequestDTO(Integer page, Integer size) {

  public GetAdminBoardPostCommentsCommand toCommand(Long operatorUserId, Long postId) {
    AdminPageQuery pageQuery =
        AdminPageQueryNormalizer.normalize(
            page, size, null, null, AdminBoardPagePolicies.POST_COMMENTS);
    return new GetAdminBoardPostCommentsCommand(
        operatorUserId, postId, pageQuery.page(), pageQuery.size());
  }
}
