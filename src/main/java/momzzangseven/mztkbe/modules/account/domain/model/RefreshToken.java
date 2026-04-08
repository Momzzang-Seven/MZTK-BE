package momzzangseven.mztkbe.modules.account.domain.model;

import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.token.RefreshTokenInvalidException;

/**
 * Refresh Token Domain Model.
 *
 * <p>Responsibilities (Information Expert Pattern): - Validate token expiration - Check if token is
 * revoked - Track token usage - Enforce business rules for token lifecycle.
 *
 * <p>Business Rules: - Token can only be used if not expired and not revoked - Token can be revoked
 * at any time - Token usage is tracked for security audit.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RefreshToken {

  /** Maximum allowed token validity period (in days). */
  private static final int MAX_TOKEN_DAYS = 7;

  /** Unique identifier (database primary key). */
  private final Long id;

  /** User ID who owns this token. */
  private final Long userId;

  /** Actual JWT token value (encrypted string). */
  private final String tokenValue;

  /** Token expiration timestamp. */
  private final Instant expiresAt;

  /** Token Revocation timestamp. */
  private final Instant revokedAt;

  /** Token creation timestamp. */
  private final Instant createdAt;

  /** Last time this token was used for reissuing. */
  private final Instant usedAt;

  // ============================================
  // Factory Methods
  // ============================================

  /**
   * Create a new Refresh Token.
   *
   * @param userId User ID
   * @param tokenValue JWT token string
   * @param expiresAt Expiration timestamp
   * @param createdAt Creation timestamp (used as reference for validation)
   * @return New RefreshToken instance
   * @throws IllegalArgumentException if validation fails
   */
  public static RefreshToken create(
      Long userId, String tokenValue, Instant expiresAt, Instant createdAt) {
    validateUserId(userId);
    validateTokenValue(tokenValue);
    validateExpiresAt(expiresAt, createdAt);

    return RefreshToken.builder()
        .userId(userId)
        .tokenValue(tokenValue)
        .expiresAt(expiresAt)
        .revokedAt(null)
        .createdAt(createdAt)
        .usedAt(null)
        .build();
  }

  // ============================================
  // Business Logic Methods
  // ============================================

  /**
   * Check if this token is valid for use.
   *
   * <p>Business Rule: Token is valid only if: 1. Not expired 2. Not revoked
   *
   * @param now Current instant for time comparison
   * @return true if token can be used, false otherwise
   */
  public boolean isValid(Instant now) {
    return !isExpired(now) && !isRevoked();
  }

  /**
   * Check if token is revoked.
   *
   * @return true if revokedAt is not null
   */
  public boolean isRevoked() {
    return revokedAt != null;
  }

  /**
   * Check if token has expired.
   *
   * @param now Current instant for time comparison
   * @return true if current time is after expiration time
   */
  public boolean isExpired(Instant now) {
    return now.isAfter(expiresAt);
  }

  /**
   * Mark this token as used.
   *
   * <p>Business Rule: Track usage for security audit and potential token rotation detection.
   *
   * @param now Current instant for the used-at timestamp
   * @throws IllegalStateException if token is not valid
   */
  public RefreshToken markAsUsed(Instant now) {
    if (!isValid(now)) {
      throw new RefreshTokenInvalidException("Cannot mark invalid token as used");
    }

    return this.toBuilder().usedAt(now).build();
  }

  /**
   * Revoke (invalidate) this token.
   *
   * <p>Business Rule: Once revoked, token cannot be used anymore. This is typically done when: -
   * User logs out - Security breach detected - Token rotation (old token replaced with new one)
   *
   * @param now Current instant for the revoked-at timestamp
   */
  public RefreshToken revoke(Instant now) {
    if (this.isRevoked()) {
      return this;
    }

    return this.toBuilder().revokedAt(now).build();
  }

  /**
   * Check if token was recently used (within given minutes).
   *
   * <p>This can be used for detecting token replay attacks.
   *
   * @param withinMinutes Time window in minutes
   * @param now Current instant for time comparison
   * @return true if token was used within the time window
   */
  public boolean wasRecentlyUsed(int withinMinutes, Instant now) {
    if (usedAt == null) {
      return false;
    }

    Instant threshold = now.minus(Duration.ofMinutes(withinMinutes));
    return usedAt.isAfter(threshold);
  }

  /**
   * Get remaining time until expiration in seconds.
   *
   * @param now Current instant for time comparison
   * @return Remaining seconds, or 0 if already expired
   */
  public long getRemainingSeconds(Instant now) {
    if (isExpired(now) || isRevoked()) {
      return 0;
    }

    return Duration.between(now, expiresAt).getSeconds();
  }

  /**
   * Check if this token is about to expire soon.
   *
   * @param thresholdMinutes Minutes before expiration
   * @param now Current instant for time comparison
   * @return true if token will expire within threshold
   */
  public boolean isExpiringSoon(int thresholdMinutes, Instant now) {
    if (isExpired(now)) {
      return true;
    }

    long remainingMinutes = getRemainingSeconds(now) / 60;
    return remainingMinutes <= thresholdMinutes;
  }

  // ============================================
  // Validation Methods (Private)
  // ============================================

  /** Validate user ID. */
  private static void validateUserId(Long userId) {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("User ID must be a positive number");
    }
  }

  /** Validate token value. */
  private static void validateTokenValue(String tokenValue) {
    if (tokenValue == null || tokenValue.isBlank()) {
      throw new IllegalArgumentException("Token value is required");
    }

    if (tokenValue.length() < 10) {
      throw new IllegalArgumentException("Token value too short");
    }

    if (tokenValue.length() > 500) {
      throw new IllegalArgumentException("Token value too long");
    }
  }

  /**
   * Validate expiration timestamp against a reference time. This method is used when a new Refresh
   * Token is created. This method checks whether the provided expiration date is valid or not.
   *
   * @param expiresAt Expiration timestamp to validate
   * @param referenceTime Reference point for validation (typically createdAt)
   */
  private static void validateExpiresAt(Instant expiresAt, Instant referenceTime) {
    if (expiresAt == null) {
      throw new IllegalArgumentException("Expiration time is required");
    }

    if (expiresAt.isBefore(referenceTime)) {
      throw new IllegalArgumentException("Expiration time must be in the future");
    }

    Instant maxExpiration = referenceTime.plus(Duration.ofDays(MAX_TOKEN_DAYS));
    if (expiresAt.isAfter(maxExpiration)) {
      throw new IllegalArgumentException(
          "Token expiration cannot exceed " + MAX_TOKEN_DAYS + " days");
    }
  }

  // ============================================
  // Object Methods
  // ============================================

  @Override
  public String toString() {
    return "RefreshToken{"
        + "id="
        + id
        + ", userId="
        + userId
        + ", revoked="
        + isRevoked()
        + ", revokedAt="
        + revokedAt
        + ", expiresAt="
        + expiresAt
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    RefreshToken that = (RefreshToken) o;
    return tokenValue != null && tokenValue.equals(that.tokenValue);
  }

  @Override
  public int hashCode() {
    return tokenValue != null ? tokenValue.hashCode() : 0;
  }
}
