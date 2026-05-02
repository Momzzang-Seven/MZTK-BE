package momzzangseven.mztkbe.modules.admin.user.application.port.out;

import momzzangseven.mztkbe.modules.user.domain.model.UserRole;

/** Output port for loading a target user's role through the user module. */
public interface LoadAdminUserRolePort {

  UserRole load(Long userId);
}
