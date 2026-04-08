package momzzangseven.mztkbe.modules.account.application.delegation;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadAccountUserInfoPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveRefreshTokenPort;
import momzzangseven.mztkbe.modules.account.domain.model.RefreshToken;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
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
  private final LoadAccountUserInfoPort loadAccountUserInfoPort;

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
    AccountUserSnapshot snapshot =
        loadAccountUserInfoPort
            .findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

    String newAccessToken =
        jwtTokenProvider.generateAccessToken(
            snapshot.userId(), snapshot.email(), UserRole.valueOf(snapshot.role()));
    log.debug(
        "New access token generated for user {} (length: {})",
        snapshot.userId(),
        newAccessToken.length());

    String newRefreshTokenValue = createAndSaveRefreshToken(snapshot.userId());

    revokeToken(oldRefreshToken);

    log.info("Token rotation completed for userId: {}", snapshot.userId());
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

    String refreshTokenValue = jwtTokenProvider.generateRefreshToken(userId);

    Instant now = Instant.now();
    Instant expiresAt = now.plus(Duration.ofMillis(jwtTokenProvider.getRefreshTokenExpiresIn()));

    RefreshToken refreshToken = RefreshToken.create(userId, refreshTokenValue, expiresAt, now);

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
    RefreshToken revokedToken = refreshToken.revoke(Instant.now());
    saveRefreshTokenPort.save(revokedToken);
    log.debug("Token revoked");
  }
}
