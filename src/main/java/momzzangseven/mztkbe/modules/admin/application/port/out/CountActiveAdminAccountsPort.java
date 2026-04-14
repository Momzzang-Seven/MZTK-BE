package momzzangseven.mztkbe.modules.admin.application.port.out;

/** Output port for counting the number of active admin accounts. */
public interface CountActiveAdminAccountsPort {

  long countActive();

  /**
   * Count active admin accounts whose user record has the given role.
   *
   * @param roleName the {@code UserRole} enum name stored in the users table (e.g. "ADMIN_SEED")
   * @return number of matching active admin accounts
   */
  long countActiveByRole(String roleName);
}
