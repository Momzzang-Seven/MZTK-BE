package momzzangseven.mztkbe.modules.admin.user.application.dto;

import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserAccountStatus;

/** Command for fetching the admin user-management list. */
public record GetAdminUsersCommand(
    Long operatorUserId,
    String search,
    AdminUserAccountStatus status,
    AdminUserRoleFilter role,
    int page,
    int size,
    AdminUserSortKey sortKey) {

  public GetAdminUsersCommand {
    if (operatorUserId == null || operatorUserId <= 0) {
      throw new IllegalArgumentException("operatorUserId must be positive");
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
