package momzzangseven.mztkbe.modules.user.domain.model;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.user.IllegalAdminGrantException;
import momzzangseven.mztkbe.global.error.user.InvalidUserRoleException;

/** Domain model representing an application user. */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {
  private final Long id;
  private final String email;
  private final String nickname;
  private final String profileImageUrl;
  private final UserRole role;
  private final Instant createdAt;
  private final Instant updatedAt;

  // ============================================
  // Factory Methods (Creator Pattern)
  // ============================================

  /**
   * Create a user with profile data. Used by the account module during signup — authentication
   * fields are managed separately in {@code users_account}.
   *
   * @param email User's email address
   * @param nickname User's display name
   * @param profileImageUrl Profile image URL (nullable)
   * @param role User's role
   * @return New User instance
   * @throws IllegalArgumentException if validation fails
   */
  public static User create(String email, String nickname, String profileImageUrl, UserRole role) {
    validateEmail(email);
    validateNickname(nickname);

    // General user cannot grant himself a ADMIN role.
    if (role == UserRole.ADMIN) {
      throw new IllegalAdminGrantException();
    }

    Instant now = Instant.now();
    return User.builder()
        .email(email)
        .nickname(nickname)
        .profileImageUrl(profileImageUrl)
        .role(role)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  // ============================================
  // Business Logic Methods (Information Expert)
  // ============================================

  /**
   * Update user profile.
   *
   * @param newNickname New nickname
   * @param newProfileImageUrl New profile image URL
   * @return Updated User instance
   */
  public User updateProfile(String newNickname, String newProfileImageUrl) {
    validateNickname(newNickname);

    return this.toBuilder()
        .nickname(newNickname)
        .profileImageUrl(newProfileImageUrl)
        .updatedAt(Instant.now())
        .build();
  }

  /**
   * Update user role.
   *
   * @param newRole New role
   * @return Updated User instance
   */
  public User updateRole(UserRole newRole) {
    if (newRole == null) {
      throw new InvalidUserRoleException("Cannot update user role if no role is provided");
    }

    if (this.role == newRole) {
      throw new InvalidUserRoleException("New role is same as current role");
    }

    // Business rule: Cannot change to ADMIN (only system can do this)
    if (newRole == UserRole.ADMIN) {
      throw new IllegalAdminGrantException();
    }

    return this.toBuilder().role(newRole).updatedAt(Instant.now()).build();
  }

  /** Check if user is admin. */
  public boolean isAdmin() {
    return UserRole.ADMIN.equals(this.role);
  }

  /**
   * Check if user can change role to TRAINER. Add business rules here (e.g., email verification,
   * minimum age, etc.)
   */
  public boolean canBecomeTrainer() {
    return this.email != null;
  }

  // ============================================
  // Validation Methods
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
