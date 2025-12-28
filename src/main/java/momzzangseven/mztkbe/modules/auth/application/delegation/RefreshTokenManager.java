package momzzangseven.mztkbe.modules.auth.application.delegation;

import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.token.TokenSecurityException;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.auth.application.port.out.SaveRefreshTokenPort;
import momzzangseven.mztkbe.modules.auth.domain.model.RefreshToken;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles token rotation (issuing new tokens and revoking old ones).
 *
 * <p>Single Responsibility: Token rotation
 *
 * <p>Security: Implements OAuth 2.0 token rotation best practice
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RefreshTokenManager {
  private final JwtTokenProvider jwtTokenProvider;
  private final SaveRefreshTokenPort saveRefreshTokenPort;

  /** Result of token rotation. */
  public record TokenPair(String accessToken, String refreshToken) {}

  /**
   * Generate new token pair and revoke old refresh token.
   *
   * @param user User information
   * @param oldRefreshToken Old refresh token to revoke
   * @return New token pair (access + refresh)
   */
  public TokenPair rotateTokens(User user, RefreshToken oldRefreshToken) {
    log.info("Starting token rotation for user: {}", user.getId());

    // 1. Generate new access token
    String newAccessToken =
        jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
    log.debug("New access token generated");

    // 2. Generate and save new refresh token
    String newRefreshTokenValue = createAndSaveRefreshToken(user.getId());

    // 3. Revoke old refresh token
    revokeToken(oldRefreshToken);

    log.info("Token rotation completed for userId: {}", user.getId());
    return new TokenPair(newAccessToken, newRefreshTokenValue);
  }

  /**
   * Create and save a new refresh token for a user.
   *
   * @param userId User's unique identifier
   * @return JWT refresh token string
   */
  public String createAndSaveRefreshToken(Long userId) {
    log.debug("Creating refresh token for userId: {}", userId);

    // 1. Generate JWT refresh token
    String refreshTokenValue = jwtTokenProvider.generateRefreshToken(userId);

    // 2. Calculate when the token expires at
    LocalDateTime expiresAt =
        LocalDateTime.now().plus(Duration.ofMillis(jwtTokenProvider.getRefreshTokenExpiresIn()));

    // 3. Create domain model
    RefreshToken refreshToken = RefreshToken.create(userId, refreshTokenValue, expiresAt);

    // 4. Persist to database
    saveRefreshTokenPort.save(refreshToken);
    log.debug("Refresh token saved to database for userId: {}", userId);

    return refreshTokenValue;
  }

  /**
   * Check for token reuse (possible replay attack).
   *
   * <p>Security: If a token was recently used, it's suspicious
   *
   * <p>Possible scenarios: - Replay attack - Token hijacking - Race condition (legitimate but rare)
   *
   * @param refreshToken Token to check
   * @param thresholdMinutes Time window for reuse detection (e.g., 5 minutes)
   * @throws TokenSecurityException if reuse detected
   */
  public void checkTokenReuse(RefreshToken refreshToken, int thresholdMinutes) {
    if (refreshToken.wasRecentlyUsed(thresholdMinutes)) {
      log.error(
          "Token reuse detected! Possible replay attack. userId={}", refreshToken.getUserId());

      // Security measure: Revoke token immediately
      revokeToken(refreshToken);

      throw new TokenSecurityException("Token reuse detected");
    }

    log.debug("No token reuse detected");
  }

  /**
   * Mark token as used (audit trail).
   *
   * <p>Purpose: - Track token usage for security audit - Enable token reuse detection
   *
   * @param refreshToken Token to mark
   */
  public void markTokenUsed(RefreshToken refreshToken) {
    refreshToken.markAsUsed();
    saveRefreshTokenPort.save(refreshToken);
    log.debug("Token marked as used at: {}", refreshToken.getUsedAt());
  }

  public void revokeToken(RefreshToken refreshToken) {
    log.debug("Revoking refresh token for userId: {}", refreshToken.getUserId());
    refreshToken.revoke();
    saveRefreshTokenPort.save(refreshToken);
    log.debug("Token revoked");
  }
}
