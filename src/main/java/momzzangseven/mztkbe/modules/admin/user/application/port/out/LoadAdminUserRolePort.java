package momzzangseven.mztkbe.modules.admin.user.application.port.out;

import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserRole;

/** Output port for loading a target user's role through the user module. */
public interface LoadAdminUserRolePort {

  AdminUserRole load(Long userId);
}
