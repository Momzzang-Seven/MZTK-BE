package momzzangseven.mztkbe.modules.auth.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Authentication provider enumeration.
 *
 * <p>Defines the supported authentication methods in the system. Used by the Strategy Pattern to
 * select appropriate authentication logic.
 *
 * <p>Supported providers: - LOCAL: Email/password based authentication. - KAKAO: Kakao OAuth
 * authentication. - GOOGLE: Google OAuth authentication.
 */
@Getter
@RequiredArgsConstructor
public enum AuthProvider {
  /**
   * Local authentication (email/password) for direct sign-ups.
   */
  LOCAL("LOCAL", "Email/Password Authentication"),

  /** Kakao OAuth authentication for Kakao users. */
  KAKAO("KAKAO", "Kakao OAuth Authentication"),

  /** Google OAuth authentication for Google users. */
  GOOGLE("GOOGLE", "Google OAuth Authentication");

  /** Provider display name. */
  private final String displayName;

  /** Provider description. */
  private final String description;

  /**
   * Check if this provider is a social login provider.
   *
   * @return true if this is a social login provider (KAKAO or GOOGLE)
   */
  public boolean isSocialLogin() {
    return this == KAKAO || this == GOOGLE;
  }

  /**
   * Check if this provider requires OAuth flow.
   *
   * @return true if OAuth flow is required
   */
  public boolean requiresOAuth() {
    return isSocialLogin();
  }

  /**
   * Check if this provider requires email/password.
   *
   * @return true if email/password is required (LOCAL only)
   */
  public boolean requiresCredentials() {
    return this == LOCAL;
  }
}
