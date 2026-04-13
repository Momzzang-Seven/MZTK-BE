package momzzangseven.mztkbe.modules.account.application.dto;

import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;

/**
 * Login command passed to LoginUseCase.
 *
 * <p>Converted from API layer's LoginRequest.
 */
public record LoginCommand(
    AuthProvider provider,
    String email,
    String password,
    String authorizationCode,
    String redirectUri,
    String role,
    String loginId) {

  /**
   * Validate command based on provider.
   *
   * @throws IllegalArgumentException if validation fails
   */
  public void validate() {
    if (provider == null) {
      throw new IllegalArgumentException("Provider is required");
    }

    switch (provider) {
      case LOCAL:
        if (email == null || email.isBlank()) {
          throw new IllegalArgumentException("Email is required for LOCAL login");
        }
        if (password == null || password.isBlank()) {
          throw new IllegalArgumentException("Password is required for LOCAL login");
        }
        break;

      case LOCAL_ADMIN:
        if (loginId == null || loginId.isBlank()) {
          throw new IllegalArgumentException("Login ID is required for LOCAL_ADMIN login");
        }
        if (password == null || password.isBlank()) {
          throw new IllegalArgumentException("Password is required for LOCAL_ADMIN login");
        }
        break;

      case KAKAO:
      case GOOGLE:
        if (authorizationCode == null || authorizationCode.isBlank()) {
          throw new IllegalArgumentException(
              "Authorization code is required for " + provider + " login");
        }
        break;

      default:
        throw new IllegalArgumentException("Unsupported provider: " + provider);
    }
  }
}
