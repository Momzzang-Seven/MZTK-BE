package momzzangseven.mztkbe.modules.auth.application.delegation;

import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.auth.application.port.out.SaveRefreshTokenPort;
import momzzangseven.mztkbe.modules.auth.domain.model.RefreshToken;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles token rotation (issuing new tokens and revoking old ones).
 *
 * <p>Single Responsibility: Token rotation
 *
 * <p>Security: Implements OAuth 2.0 token rotation best practice
 */
@Slf4j
@Component
@Transactional
@RequiredArgsConstructor
public class RefreshTokenManager {
  private final JwtTokenProvider jwtTokenProvider;
  private final SaveRefreshTokenPort saveRefreshTokenPort;
  private final LoadUserPort loadUserPort;

  /** Result of token rotation. */
  public record TokenPair(String accessToken, String refreshToken) {}

  /**
   * Generate new token pair and revoke old refresh token.
   *
   * @param userId User id
   * @param oldRefreshToken Old refresh token to revoke
   * @return New token pair (access + refresh)
   */
  public TokenPair rotateTokens(Long userId, RefreshToken oldRefreshToken) {
    log.info("Starting token rotation for user: {}", userId);
    User user =
        loadUserPort.loadUserById(userId).orElseThrow(() -> new UserNotFoundException(userId));

    // 1. Generate new access token
    String newAccessToken =
        jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
    log.debug(
        "New access token generated for user {} (length: {})",
        user.getId(),
        newAccessToken.length());

    // 2. Generate and save new refresh token
    String newRefreshTokenValue = createAndSaveRefreshToken(user.getId());

    // 3. Revoke old refresh token
    revokeToken(oldRefreshToken);

    log.info("Token rotation completed for userId: {}", user.getId());
    return new TokenPair(newAccessToken, newRefreshTokenValue);
  }

  /**
   * Create and persist a new refresh token for the user.
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
    RefreshToken refreshToken =
        RefreshToken.create(userId, refreshTokenValue, expiresAt, LocalDateTime.now());

    // 4. Persist to database
    saveRefreshTokenPort.save(refreshToken);
    log.debug("Refresh token saved to database for userId: {}", userId);

    return refreshTokenValue;
  }

  /**
   * Revoke the provided refresh token and persist the change.
   *
   * @param refreshToken Refresh token to revoke
   */
  public void revokeToken(RefreshToken refreshToken) {
    log.debug("Revoking refresh token for userId: {}", refreshToken.getUserId());
    refreshToken.revoke();
    saveRefreshTokenPort.save(refreshToken);
    log.debug("Token revoked");
  }
}
