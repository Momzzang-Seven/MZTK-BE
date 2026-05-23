package momzzangseven.mztkbe.modules.admin.board.api.dto;

import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardPagePolicies;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardPostSortKey;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostsCommand;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostModerationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostPublicationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostType;
import momzzangseven.mztkbe.modules.admin.common.application.dto.AdminPageQuery;
import momzzangseven.mztkbe.modules.admin.common.application.dto.AdminPageQueryNormalizer;

/** Query DTO for {@code GET /admin/boards/posts}. */
public record GetAdminBoardPostsRequestDTO(
    String search,
    Long postId,
    Long userId,
    AdminBoardPostStatus status,
    AdminBoardPostType type,
    AdminBoardPostPublicationStatus publicationStatus,
    AdminBoardPostModerationStatus moderationStatus,
    Integer page,
    Integer size,
    String sort) {

  public GetAdminBoardPostsCommand toCommand(Long operatorUserId) {
    AdminPageQuery pageQuery =
        AdminPageQueryNormalizer.normalize(page, size, search, sort, AdminBoardPagePolicies.POSTS);
    return new GetAdminBoardPostsCommand(
        operatorUserId,
        pageQuery.search(),
        postId,
        userId,
        status,
        type,
        publicationStatus,
        moderationStatus,
        pageQuery.page(),
        pageQuery.size(),
        AdminBoardPostSortKey.from(pageQuery.sort()));
  }
}
