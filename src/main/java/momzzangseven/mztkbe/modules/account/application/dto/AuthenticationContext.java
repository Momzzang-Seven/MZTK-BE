package momzzangseven.mztkbe.modules.account.application.dto;

import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;

/**
 * Authentication context passed to AuthenticationStrategy.
 *
 * <p>This DTO contains all necessary information for authentication, regardless of the
 * authentication method (LOCAL, KAKAO, GOOGLE).
 *
 * <p>Different strategies use different fields: - LOCAL: email, password - KAKAO:
 * authorizationCode, redirectUri - GOOGLE: authorizationCode, redirectUri
 *
 * @param provider Authentication provider (LOCAL, KAKAO, GOOGLE)
 * @param email Email address (for LOCAL authentication)
 * @param password Password (for LOCAL authentication)
 * @param authorizationCode Authorization code from OAuth callback (for KAKAO, GOOGLE)
 * @param redirectUri Redirect URI used in OAuth flow (optional, for validation)
 */
public record AuthenticationContext(
    AuthProvider provider,
    String email,
    String password,
    String authorizationCode,
    String redirectUri,
    String role,
    String loginId) {

  /**
   * Create AuthenticationContext from LoginCommand.
   *
   * @param command LoginCommand from use case
   * @return AuthenticationContext for strategy
   */
  public static AuthenticationContext from(LoginCommand command) {
    return new AuthenticationContext(
        command.provider(),
        command.email(),
        command.password(),
        command.authorizationCode(),
        command.redirectUri(),
        command.role(),
        command.loginId());
  }

  /**
   * Validate context for LOCAL authentication.
   *
   * @return true if email and password are present
   */
  public boolean isValidForLocal() {
    return email != null && !email.isBlank() && password != null && !password.isBlank();
  }

  /**
   * Validate context for social authentication (KAKAO, GOOGLE).
   *
   * @return true if authorizationCode is present
   */
  public boolean isValidForSocial() {
    return authorizationCode != null && !authorizationCode.isBlank();
  }

  /**
   * Validate context for LOCAL_ADMIN authentication.
   *
   * @return true if loginId and password are present
   */
  public boolean isValidForLocalAdmin() {
    return loginId != null && !loginId.isBlank() && password != null && !password.isBlank();
  }
}
