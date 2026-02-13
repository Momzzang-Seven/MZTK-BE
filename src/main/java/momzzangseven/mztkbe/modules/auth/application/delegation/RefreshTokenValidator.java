package momzzangseven.mztkbe.modules.auth.application.delegation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.RefreshTokenInvalidException;
import momzzangseven.mztkbe.global.error.RefreshTokenNotFoundException;
import momzzangseven.mztkbe.global.error.TokenSecurityException;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.auth.application.port.out.LoadRefreshTokenPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.SaveRefreshTokenPort;
import momzzangseven.mztkbe.modules.auth.domain.model.RefreshToken;
import org.springframework.stereotype.Component;

/**
 * Validates refresh token through multiple security checks.
 *
 * <p>Single Responsibility: Validation only
 *
 * <p>Used by ReissueTokenService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenValidator {

  private final JwtTokenProvider jwtTokenProvider;
  private final LoadRefreshTokenPort loadRefreshTokenPort;
  private final SaveRefreshTokenPort saveRefreshTokenPort;
  private final RefreshTokenManager refreshTokenManager;

  /**
   * Validate JWT format and signature.
   *
   * @param tokenValue JWT token string
   * @throws RefreshTokenNotFoundException if invalid
   */
  public void validateJwtFormat(String tokenValue) {
    if (!jwtTokenProvider.validateToken(tokenValue)) {
      log.warn("Invalid JWT token format or signature");
      throw new RefreshTokenNotFoundException("invalid JWT token format or signature.");
    }

    if (!jwtTokenProvider.isRefreshToken(tokenValue)) {
      log.warn("Token is not a refresh token");
      throw new RefreshTokenNotFoundException("sent token is not a refresh token.");
    }

    log.debug("JWT format validation passed");
  }

  /**
   * Load refresh token from database by its value with lock.
   *
   * @param tokenValue Token string to find
   * @return Validated RefreshToken domain model
   * @throws RefreshTokenNotFoundException if not found
   */
  public RefreshToken loadTokenByValueWithLock(String tokenValue) {
    log.info("Attempting to acquire lock for token");
    RefreshToken token =
        loadRefreshTokenPort
            .findByTokenValueWithLock(tokenValue)
            .orElseThrow(
                () -> {
                  log.error("Refresh token not found in database");
                  return new RefreshTokenNotFoundException("Refresh token not found in database.");
                });

    log.debug("Token loaded from database with lock: {}", token);
    return token;
  }

  /**
   * Validate userId consistency between JWT and DB. If user id of from the token submitted is
   * different with the one in DB, the exception occurs.
   *
   * @param jwtUserId UserId from JWT claim
   * @param refreshToken RefreshToken from DB
   * @throws TokenSecurityException if mismatch
   */
  public void validateUserIdConsistency(Long jwtUserId, RefreshToken refreshToken) {
    if (!refreshToken.getUserId().equals(jwtUserId)) {
      log.error(
          "SECURITY: Token userId mismatch! JWT={}, DB={}", jwtUserId, refreshToken.getUserId());

      // Revoke suspicious token
      refreshTokenManager.revokeToken(refreshToken);

      throw new TokenSecurityException();
    }

    log.debug("UserId consistency validated");
  }

  /**
   * Validate domain business rules (expiration, revocation).
   *
   * @param refreshToken RefreshToken to validate
   * @throws RefreshTokenInvalidException if expired
   * @throws RefreshTokenInvalidException if revoked
   */
  public void validateDomainRules(RefreshToken refreshToken) {
    if (!refreshToken.isValid()) {
      if (refreshToken.isExpired()) {
        log.warn("Refresh token expired: userId={}", refreshToken.getUserId());
        throw new RefreshTokenInvalidException("expired");
      }
      if (refreshToken.isRevoked()) {
        log.warn("Refresh token revoked: userId={}", refreshToken.getUserId());
        throw new RefreshTokenInvalidException("revoked");
      }
    }

    log.debug("Domain rules validation passed");
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
      refreshTokenManager.revokeToken(refreshToken);

      throw new TokenSecurityException("Token reuse detected");
    }

    log.debug("No token reuse detected");
  }

  /**
   * Mark token as used.
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

  /**
   * Inspect refresh token for security anomalies and mark it used.
   *
   * @param tokenValue Token string submitted by client
   * @param jwtUserId User ID extracted from JWT
   * @return Validated refresh token entity
   */
  public RefreshToken inspectSecurityFlaw(String tokenValue, Long jwtUserId) {
    // Step 1: Load token from DB: Lock acquisition
    RefreshToken dbRefreshToken = loadTokenByValueWithLock(tokenValue);

    // Step 2: Check Consistency between UserId from request and from DB
    validateUserIdConsistency(jwtUserId, dbRefreshToken);

    // Step 3: validate domain rule
    validateDomainRules(dbRefreshToken);

    // Step 4: Check for the token reuse
    checkTokenReuse(dbRefreshToken, 5);

    // Step 5: mark token as used. Update the row from the DB
    markTokenUsed(dbRefreshToken);

    return dbRefreshToken;
  }
}
