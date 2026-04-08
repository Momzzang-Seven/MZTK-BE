package momzzangseven.mztkbe.modules.account.application.dto;

/**
 * Result of authentication process.
 *
 * <p>Returned by AuthenticationStrategy after successful authentication. Contains authenticated
 * user snapshot and additional context.
 *
 * @param userSnapshot Account-internal user profile snapshot
 * @param isNewUser Whether this is a newly created user (auto-registered via social login)
 */
public record AuthenticatedUser(AccountUserSnapshot userSnapshot, boolean isNewUser) {

  /**
   * Create AuthenticatedUser for existing user.
   *
   * @param userSnapshot Existing user snapshot
   * @return AuthenticatedUser with isNewUser=false
   */
  public static AuthenticatedUser existing(AccountUserSnapshot userSnapshot) {
    return new AuthenticatedUser(userSnapshot, false);
  }

  /**
   * Create AuthenticatedUser for new user.
   *
   * @param userSnapshot Newly created user snapshot
   * @return AuthenticatedUser with isNewUser=true
   */
  public static AuthenticatedUser newUser(AccountUserSnapshot userSnapshot) {
    return new AuthenticatedUser(userSnapshot, true);
  }
}
