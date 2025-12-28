package momzzangseven.mztkbe.modules.user.domain.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Getter
@Builder
public class User {
  private Long id;
  private String email;

  /** BCrypt-encoded password (only for LOCAL auth) Format: $2a$10$... (60 characters) */
  private String password;

  private String nickname;
  private String profileImageUrl;

  /**
   * Provider-specific user ID. - KAKAO: Kakao user ID (String) - GOOGLE: Google user ID (String) -
   * LOCAL: null
   */
  private String providerUserId;

  /** Connected Web3 wallet address */
  private String walletAddress;

  private AuthProvider authProvider;
  private UserRole role;
  private LocalDateTime lastLoginAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  // ============================================
  // Factory Methods (Creator Pattern)
  // ============================================

  /**
   * Create a LOCAL user (email/password authentication).
   *
   * @param email User's email address
   * @param encodedPassword BCrypt-encoded password
   * @param nickname User's display name
   * @return New User instance
   * @throws IllegalArgumentException if validation fails
   */
  public static User createFromLocal(String email, String encodedPassword, String nickname) {
    validateEmail(email);
    validateEncodedPassword(encodedPassword);
    validateNickname(nickname);

    LocalDateTime now = LocalDateTime.now();

    return User.builder()
        .email(email)
        .password(encodedPassword)
        .nickname(nickname)
        .authProvider(AuthProvider.LOCAL)
        .role(UserRole.USER)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  /**
   * Create a new user from Social OAuth data (Kakao/Google/etc).
   *
   * @param provider AuthProvider (KAKAO, GOOGLE, etc.)
   * @param providerUserId Unique ID from the provider
   * @param email User's email
   * @param nickname User's nickname (must not be null/blank)
   * @param profileImageUrl Profile image URL
   * @return New User instance
   */
  public static User createFromSocial(
      AuthProvider provider,
      String providerUserId,
      String email,
      String nickname,
      String profileImageUrl) {
    if (provider == null || !provider.isSocialLogin()) {
      throw new IllegalArgumentException("Invalid social provider: " + provider);
    }
    if (providerUserId == null || providerUserId.isBlank()) {
      throw new IllegalArgumentException("Provider User ID is required");
    }
    validateEmail(email);
    validateNickname(nickname);

    LocalDateTime now = LocalDateTime.now();
    return User.builder()
        .authProvider(provider)
        .providerUserId(providerUserId)
        .email(email)
        .nickname(nickname)
        .profileImageUrl(profileImageUrl)
        .role(UserRole.USER)
        .lastLoginAt(now)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  // ============================================
  // Business Logic Methods (Information Expert)
  // ============================================

  /**
   * Validates the password for LOCAL authentication.
   *
   * @param rawPassword plain text password from login request
   * @param encoder BCrypt password encoder
   * @return true if password matches, false otherwise
   * @throws IllegalStateException if called on a non-LOCAL user
   * @throws BusinessException if a critical system error occurs during validation
   */
  public boolean validatePassword(String rawPassword, PasswordEncoder encoder) {
    // Business Rule 1: Only LOCAL users can validate passwords
    if (!AuthProvider.LOCAL.equals(this.authProvider)) {
      log.error(
          "Password validation attempted on non-LOCAL user: authProvider={}", this.authProvider);
      throw new IllegalStateException(
          "Password validation is only allowed for LOCAL users. "
              + "This user uses: "
              + this.authProvider);
    }

    // Business Rule 2: Password field must exist
    if (this.password == null || this.password.isBlank()) {
      log.warn("User {} has no password set", this.id);
      return false;
    }

    // Business Rule 3: Input password must not be empty
    if (rawPassword == null || rawPassword.isBlank()) {
      log.debug("Empty password provided for validation");
      return false;
    }

    // Validate using BCrypt (constant-time comparison)
    try {
      boolean matches = encoder.matches(rawPassword, this.password);

      if (matches) {
        log.debug("Password validation successful for user: {}", this.id);
      } else {
        log.warn("Password validation failed for user: {}", this.id);
      }

      return matches;
    } catch (Exception e) {
      log.error("Critical error during password validation for user: {}", this.id, e);
      // We throw a generic BusinessException with INTERNAL_SERVER_ERROR or DATABASE_ERROR
      // but here it's more like a system error.
      throw new BusinessException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Error during password validation", e);
    }
  }

  /**
   * Update user profile.
   *
   * @param nickname New nickname
   * @param profileImageUrl New profile image URL
   */
  public void updateProfile(String nickname, String profileImageUrl) {
    validateNickname(nickname);
    this.nickname = nickname;
    this.profileImageUrl = profileImageUrl;
    this.updatedAt = LocalDateTime.now();
  }

  /**
   * Update password (LOCAL users only).
   *
   * @param newEncodedPassword New BCrypt-encoded password
   * @throws IllegalStateException if called on non-LOCAL user
   */
  public void updatePassword(String newEncodedPassword) {
    if (!AuthProvider.LOCAL.equals(this.authProvider)) {
      throw new IllegalStateException("Password update is only allowed for LOCAL users");
    }
    validateEncodedPassword(newEncodedPassword);
    this.password = newEncodedPassword;
    this.updatedAt = LocalDateTime.now();
  }

  /** Update last login timestamp. */
  public void updateLastLogin() {
    this.lastLoginAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
    log.debug("Updated last login time for user: {}", this.id);
  }

  /** Check if user is admin. */
  public boolean isAdmin() {
    return UserRole.ADMIN.equals(this.role);
  }

  // ============================================
  // Validation Methods (Private)
  // ============================================

  /** Validate email format. */
  private static void validateEmail(String email) {
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Email is required");
    }

    // Basic email regex
    String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    if (!email.matches(emailRegex)) {
      throw new IllegalArgumentException("Invalid email format: " + email);
    }
  }

  /**
   * Validate encoded password format.
   *
   * <p>BCrypt format: $2a$10$... (60 characters total)
   */
  private static void validateEncodedPassword(String encodedPassword) {
    if (encodedPassword == null || encodedPassword.isBlank()) {
      throw new IllegalArgumentException("Password is required");
    }

    // BCrypt format check
    if (!encodedPassword.matches("^\\$2[ayb]\\$.{56}$")) {
      throw new IllegalArgumentException("Password must be BCrypt encoded format");
    }
  }

  /** Validate nickname. */
  private static void validateNickname(String nickname) {
    if (nickname == null || nickname.isBlank()) {
      throw new IllegalArgumentException("Nickname is required");
    }

    if (nickname.length() < 2 || nickname.length() > 50) {
      throw new IllegalArgumentException("Nickname must be between 2 and 50 characters");
    }
  }
}
