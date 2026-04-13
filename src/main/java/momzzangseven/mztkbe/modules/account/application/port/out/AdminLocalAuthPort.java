package momzzangseven.mztkbe.modules.account.application.port.out;

/**
 * Output port for authenticating admin local credentials. The implementation delegates to the admin
 * module's {@code AuthenticateAdminLocalUseCase}.
 */
public interface AdminLocalAuthPort {

  /**
   * Authenticate admin credentials and return the linked user ID.
   *
   * @param loginId the admin login identifier
   * @param password the raw password
   * @return the user ID linked to the authenticated admin account
   * @throws momzzangseven.mztkbe.global.error.InvalidCredentialsException if credentials are
   *     invalid
   */
  Long authenticateAndGetUserId(String loginId, String password);
}
