package momzzangseven.mztkbe.modules.admin.user.api.dto;

import momzzangseven.mztkbe.modules.admin.common.application.dto.AdminPageQuery;
import momzzangseven.mztkbe.modules.admin.common.application.dto.AdminPageQueryNormalizer;
import momzzangseven.mztkbe.modules.admin.user.application.dto.AdminUserPagePolicies;
import momzzangseven.mztkbe.modules.admin.user.application.dto.AdminUserRoleFilter;
import momzzangseven.mztkbe.modules.admin.user.application.dto.AdminUserSortKey;
import momzzangseven.mztkbe.modules.admin.user.application.dto.GetAdminUsersCommand;
import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserAccountStatus;

/** Query DTO for {@code GET /admin/users}. */
public record GetAdminUsersRequestDTO(
    String search,
    AdminUserAccountStatus status,
    AdminUserRoleFilter role,
    Integer page,
    Integer size,
    String sort) {

  public GetAdminUsersCommand toCommand(Long operatorUserId) {
    AdminPageQuery pageQuery =
        AdminPageQueryNormalizer.normalize(page, size, search, sort, AdminUserPagePolicies.USERS);
    return new GetAdminUsersCommand(
        operatorUserId,
        pageQuery.search(),
        status,
        role,
        pageQuery.page(),
        pageQuery.size(),
        AdminUserSortKey.from(pageQuery.sort()));
  }
}
