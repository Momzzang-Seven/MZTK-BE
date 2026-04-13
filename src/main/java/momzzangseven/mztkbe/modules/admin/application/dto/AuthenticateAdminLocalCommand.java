package momzzangseven.mztkbe.modules.admin.application.dto;

/**
 * Command for authenticating an admin account via local credentials.
 *
 * @param loginId numeric login identifier
 * @param password raw password to verify
 */
public record AuthenticateAdminLocalCommand(String loginId, String password) {

  public AuthenticateAdminLocalCommand {
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId must not be blank");
    }
    if (password == null || password.isBlank()) {
      throw new IllegalArgumentException("password must not be blank");
    }
  }
}
