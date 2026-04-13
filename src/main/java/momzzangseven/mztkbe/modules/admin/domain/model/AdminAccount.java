package momzzangseven.mztkbe.modules.admin.domain.model;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Domain model representing an admin account. Holds login credentials (hashed) and lifecycle
 * timestamps. Linked to a {@code User} via {@code userId}.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AdminAccount {

  private final Long id;
  private final Long userId;
  private final String loginId;
  private final String passwordHash;
  private final Long createdBy;
  private final Instant lastLoginAt;
  private final Instant passwordLastRotatedAt;
  private final Instant deletedAt;
  private final Instant createdAt;
  private final Instant updatedAt;

  /**
   * Factory method to create a new admin account.
   *
   * @param userId the linked user ID
   * @param loginId the numeric login identifier
   * @param passwordHash BCrypt-hashed password
   * @param createdBy the user ID of the creating admin (nullable for seed accounts)
   * @return a new AdminAccount instance
   */
  public static AdminAccount create(
      Long userId, String loginId, String passwordHash, Long createdBy) {
    validateUserId(userId);
    validateLoginId(loginId);
    validatePasswordHash(passwordHash);

    Instant now = Instant.now();
    return AdminAccount.builder()
        .userId(userId)
        .loginId(loginId)
        .passwordHash(passwordHash)
        .createdBy(createdBy)
        .passwordLastRotatedAt(now)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  private static void validatePasswordHash(String passwordHash) {
    if (passwordHash == null || passwordHash.isBlank()) {
      throw new IllegalArgumentException("passwordHash must not be blank");
    }
  }

  private static void validateLoginId(String loginId) {
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId must not be blank");
    }
  }

  private static void validateUserId(Long userId) {
    if (userId == null) {
      throw new IllegalArgumentException("userId must not be null");
    }
  }

  /** Update the last login timestamp. */
  public AdminAccount updateLastLogin() {
    return toBuilder().lastLoginAt(Instant.now()).updatedAt(Instant.now()).build();
  }

  /** Rotate the password hash and update the rotation timestamp. */
  public AdminAccount rotatePassword(String newHash) {
    if (newHash == null || newHash.isBlank()) {
      throw new IllegalArgumentException("newHash must not be blank");
    }
    Instant now = Instant.now();
    return toBuilder().passwordHash(newHash).passwordLastRotatedAt(now).updatedAt(now).build();
  }

  /** Soft-delete this account by recording the deletion timestamp. */
  public AdminAccount softDelete() {
    if (deletedAt != null) {
      throw new IllegalStateException("Account is already deleted");
    }
    return toBuilder().deletedAt(Instant.now()).updatedAt(Instant.now()).build();
  }

  /** Check whether this account is active (not soft-deleted). */
  public boolean isActive() {
    return deletedAt == null;
  }
}
