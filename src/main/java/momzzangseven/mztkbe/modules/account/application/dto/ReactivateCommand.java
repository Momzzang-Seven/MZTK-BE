package momzzangseven.mztkbe.modules.account.application.dto;

import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;

/** Reactivation command passed to ReactivateUseCase. */
public record ReactivateCommand(
    AuthProvider provider,
    String email,
    String password,
    String authorizationCode,
    String redirectUri) {

  /** Validate command fields by provider. */
  public void validate() {
    requireProvider(provider);

    switch (provider) {
      case LOCAL -> {
        requireNonBlank(email, "Email is required for LOCAL reactivation");
        requireNonBlank(password, "Password is required for LOCAL reactivation");
        requireBlank(authorizationCode, "authorizationCode must not be provided for LOCAL");
      }
      case KAKAO, GOOGLE -> {
        requireNonBlank(
            authorizationCode, "Authorization code is required for " + provider + " reactivation");
        requireBlank(password, "password must not be provided for " + provider);
      }
      default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
    }
  }

  private static void requireProvider(AuthProvider provider) {
    if (provider == null) {
      throw new IllegalArgumentException("Provider is required");
    }
  }

  private static void requireNonBlank(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
  }

  private static void requireBlank(String value, String message) {
    if (value != null && !value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
  }
}
