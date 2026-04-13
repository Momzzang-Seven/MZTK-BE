package momzzangseven.mztkbe.modules.admin.application.port.out;

import momzzangseven.mztkbe.modules.admin.domain.vo.AdminRole;

/** Output port for creating a User record for an admin account in the users table. */
public interface CreateAdminUserPort {

  /**
   * Create a new admin user in the users table.
   *
   * @param email synthetic email address
   * @param nickname admin display name
   * @param adminRole must be ADMIN_SEED or ADMIN_GENERATED
   * @return the generated user ID
   */
  Long createAdmin(String email, String nickname, AdminRole adminRole);
}
