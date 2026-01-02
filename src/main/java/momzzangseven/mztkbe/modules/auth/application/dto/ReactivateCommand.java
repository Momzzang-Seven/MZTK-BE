package momzzangseven.mztkbe.modules.auth.application.dto;

import momzzangseven.mztkbe.modules.auth.api.dto.ReactivateRequestDTO;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;

/** Reactivation command passed to ReactivateUseCase. */
public record ReactivateCommand(
    AuthProvider provider,
    String email,
    String password,
    String authorizationCode,
    String redirectUri) {

  /** Create a command object from the API request DTO. */
  public static ReactivateCommand from(ReactivateRequestDTO request) {
    return new ReactivateCommand(
        request.getProvider(),
        request.getEmail(),
        request.getPassword(),
        request.getAuthorizationCode(),
        request.getRedirectUri());
  }

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
