package momzzangseven.mztkbe.modules.auth.domain.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.token.RefreshTokenInvalidException;

/**
 * Refresh Token Domain Model.
 *
 * <p>Responsibilities (Information Expert Pattern): - Validate token expiration - Check if token is
 * revoked - Track token usage - Enforce business rules for token lifecycle
 *
 * <p>Business Rules: - Token can only be used if not expired and not revoked - Token can be revoked
 * at any time - Token usage is tracked for security audit
 */
@Slf4j
@Getter
@Builder
public class RefreshToken {

  /** Unique identifier (database primary key) */
  private Long id;

  /** User ID who owns this token */
  private Long userId;

  /** Actual JWT token value (encrypted string) */
  private String tokenValue;

  /** Token expiration timestamp */
  private LocalDateTime expiresAt;

  /** Token Revocation timestamp */
  private LocalDateTime revokedAt;

  /** Token creation timestamp */
  private LocalDateTime createdAt;

  /** Last time this token was used for reissuing */
  private LocalDateTime usedAt;

  /** Maximum allowed token validity period (in days) */
  private static final int MAX_TOKEN_DAYS = 7;

  // ============================================
  // Factory Methods
  // ============================================

  /**
   * Create a new Refresh Token.
   *
   * @param userId User ID
   * @param tokenValue JWT token string
   * @param expiresAt Expiration timestamp
   * @return New RefreshToken instance
   * @throws IllegalArgumentException if validation fails
   */
  public static RefreshToken create(Long userId, String tokenValue, LocalDateTime expiresAt) {
    validateUserId(userId);
    validateTokenValue(tokenValue);
    validateExpiresAt(expiresAt);

    LocalDateTime now = LocalDateTime.now();

    return RefreshToken.builder()
        .userId(userId)
        .tokenValue(tokenValue)
        .expiresAt(expiresAt)
        .revokedAt(null)
        .createdAt(now)
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
   * @return true if token can be used, false otherwise
   */
  public boolean isValid() {
    boolean valid = !isExpired() && !isRevoked();

    if (!valid) {
      log.debug("Token validation failed: expired={}, revoked={}", isExpired(), isRevoked());
    }

    return valid;
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
   * @return true if current time is after expiration time
   */
  public boolean isExpired() {
    LocalDateTime now = LocalDateTime.now();
    boolean expired = now.isAfter(expiresAt);

    if (expired) {
      log.debug("Token expired: expiresAt={}, now={}", expiresAt, now);
    }

    return expired;
  }

  /**
   * Mark this token as used.
   *
   * <p>Business Rule: Track usage for security audit and potential token rotation detection.
   *
   * @throws IllegalStateException if token is not valid
   */
  public void markAsUsed() {
    if (!isValid()) {
      log.error(
          "Attempt to use invalid token: userId={}, expired={}, revoked={}",
          userId,
          isExpired(),
          isRevoked());
      throw new RefreshTokenInvalidException("Cannot mark invalid token as used");
    }

    this.usedAt = LocalDateTime.now();
    log.debug("Token marked as used: userId={}, usedAt={}", userId, usedAt);
  }

  /**
   * Revoke (invalidate) this token.
   *
   * <p>Business Rule: Once revoked, token cannot be used anymore. This is typically done when: -
   * User logs out - Security breach detected - Token rotation (old token replaced with new one)
   */
  public void revoke() {
    if (this.isRevoked()) {
      log.warn("Token already revoked at {}: userId={}", revokedAt, userId);
      return;
    }

    this.revokedAt = LocalDateTime.now();
    log.info("Token revoked: userId={}, tokenId={}, revokedAt={}", userId, id, revokedAt);
  }

  /**
   * Check if token was recently used (within given minutes).
   *
   * <p>This can be used for detecting token replay attacks.
   *
   * @param withinMinutes Time window in minutes
   * @return true if token was used within the time window
   */
  public boolean wasRecentlyUsed(int withinMinutes) {
    if (usedAt == null) {
      return false;
    }

    LocalDateTime threshold = LocalDateTime.now().minusMinutes(withinMinutes);
    return usedAt.isAfter(threshold);
  }

  /**
   * Get remaining time until expiration in seconds.
   *
   * @return Remaining seconds, or 0 if already expired
   */
  public long getRemainingSeconds() {
    if (isExpired() || isRevoked()) {
      return 0;
    }

    LocalDateTime now = LocalDateTime.now();
    return java.time.Duration.between(now, expiresAt).getSeconds();
  }

  /**
   * Check if this token is about to expire soon.
   *
   * @param thresholdMinutes Minutes before expiration
   * @return true if token will expire within threshold
   */
  public boolean isExpiringSoon(int thresholdMinutes) {
    if (isExpired()) {
      return true;
    }

    long remainingMinutes = getRemainingSeconds() / 60;
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

    // Domain only cares about: "Is there a token string?"
    // NOT: "Is it a valid JWT?"

    // Optional: Basic length check (business rule, not technical)
    if (tokenValue.length() < 10) {
      throw new IllegalArgumentException("Token value too short");
    }

    if (tokenValue.length() > 500) {
      throw new IllegalArgumentException("Token value too long");
    }
  }

  /**
   * Validate expiration timestamp. This method is used when a new Refresh Token is created. This
   * method checks whether the provided expiration date is valid or not.
   */
  private static void validateExpiresAt(LocalDateTime expiresAt) {
    if (expiresAt == null) {
      throw new IllegalArgumentException("Expiration time is required");
    }

    LocalDateTime now = LocalDateTime.now();
    if (expiresAt.isBefore(now)) {
      throw new IllegalArgumentException("Expiration time must be in the future");
    }

    // Business Rule: Token should not be valid for more than MAX_TOKEN_DAYS
    LocalDateTime maxExpiration = now.plusDays(MAX_TOKEN_DAYS);
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
        + ", expired="
        + isExpired()
        + ", revoked="
        + isRevoked()
        + ", revokedAt="
        + revokedAt
        + ", expiresAt="
        + expiresAt
        + '}';
  }

  /**
   * Check equality based on token value (business key).
   *
   * <p>Two tokens are considered equal if they have the same token value.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RefreshToken that = (RefreshToken) o;
    return tokenValue != null && tokenValue.equals(that.tokenValue);
  }

  @Override
  public int hashCode() {
    return tokenValue != null ? tokenValue.hashCode() : 0;
  }
}
