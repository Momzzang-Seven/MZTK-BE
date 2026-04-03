package momzzangseven.mztkbe.modules.account.domain.model;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;

/** Domain model representing the authentication and lifecycle data for a user account. */
@Getter
@Builder(toBuilder = true)
public class UserAccount {

  private final Long id;
  private final Long userId;
  private final AuthProvider provider;
  private final String providerUserId;
  private final String passwordHash;
  private final String googleRefreshToken;
  private final Instant lastLoginAt;
  private final AccountStatus status;
  private final Instant deletedAt;
  private final Instant createdAt;
  private final Instant updatedAt;

  // ============================================
  // Factory Methods
  // ============================================

  /**
   * Creates a new LOCAL account with an encoded password.
   *
   * @param userId the ID of the associated user record
   * @param passwordHash BCrypt-encoded password
   * @return new UserAccount with ACTIVE status
   */
  public static UserAccount createLocal(Long userId, String passwordHash) {
    Instant now = Instant.now();
    return UserAccount.builder()
        .userId(userId)
        .provider(AuthProvider.LOCAL)
        .providerUserId(null)
        .passwordHash(passwordHash)
        .status(AccountStatus.ACTIVE)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  /**
   * Creates a new social (KAKAO or GOOGLE) account.
   *
   * @param userId the ID of the associated user record
   * @param provider OAuth provider
   * @param providerUserId provider-specific unique user identifier
   * @return new UserAccount with ACTIVE status
   */
  public static UserAccount createSocial(
      Long userId, AuthProvider provider, String providerUserId) {
    Instant now = Instant.now();
    return UserAccount.builder()
        .userId(userId)
        .provider(provider)
        .providerUserId(providerUserId)
        .status(AccountStatus.ACTIVE)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  // ============================================
  // Business Logic
  // ============================================

  /**
   * Records the current time as the last successful login timestamp.
   *
   * @return updated UserAccount
   */
  public UserAccount updateLastLogin() {
    return this.toBuilder().lastLoginAt(Instant.now()).updatedAt(Instant.now()).build();
  }

  /**
   * Replaces the stored Google refresh token with a newly encrypted value.
   *
   * @param encryptedRefreshToken AES-encrypted Google refresh token
   * @return updated UserAccount
   */
  public UserAccount updateGoogleRefreshToken(String encryptedRefreshToken) {
    return this.toBuilder()
        .googleRefreshToken(encryptedRefreshToken)
        .updatedAt(Instant.now())
        .build();
  }

  /**
   * Soft-deletes the account by setting status to DELETED and recording the deletion timestamp.
   *
   * @return updated UserAccount with DELETED status
   */
  public UserAccount withdraw() {
    Instant now = Instant.now();
    return this.toBuilder().status(AccountStatus.DELETED).deletedAt(now).updatedAt(now).build();
  }

  /**
   * Reactivates a previously deleted account, clearing the deletion timestamp and updating the last
   * login time.
   *
   * @return updated UserAccount with ACTIVE status
   */
  public UserAccount reactivate() {
    Instant now = Instant.now();
    return this.toBuilder()
        .status(AccountStatus.ACTIVE)
        .deletedAt(null)
        .lastLoginAt(now)
        .updatedAt(now)
        .build();
  }

  // ============================================
  // Status Checks
  // ============================================

  /** Returns {@code true} if the account is active and usable for login. */
  public boolean isActive() {
    return status == AccountStatus.ACTIVE;
  }

  /** Returns {@code true} if the account has been soft-deleted (withdrawn). */
  public boolean isDeleted() {
    return status == AccountStatus.DELETED;
  }

  /** Returns {@code true} if the account has been blocked by an administrator. */
  public boolean isBlocked() {
    return status == AccountStatus.BLOCKED;
  }

  /** Returns {@code true} if the account is awaiting identity verification. */
  public boolean isUnverified() {
    return status == AccountStatus.UNVERIFIED;
  }
}
