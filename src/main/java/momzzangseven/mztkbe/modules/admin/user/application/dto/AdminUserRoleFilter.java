package momzzangseven.mztkbe.modules.admin.user.application.dto;

import momzzangseven.mztkbe.modules.user.domain.model.UserRole;

/** API-level role filter for the admin user-management list. */
public enum AdminUserRoleFilter {
  USER(UserRole.USER),
  TRAINER(UserRole.TRAINER);

  private final UserRole userRole;

  AdminUserRoleFilter(UserRole userRole) {
    this.userRole = userRole;
  }

  public UserRole toUserRole() {
    return userRole;
  }
}
