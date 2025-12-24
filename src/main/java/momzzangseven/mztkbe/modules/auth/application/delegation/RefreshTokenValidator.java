package momzzangseven.mztkbe.modules.auth.application.delegation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.auth.application.port.out.LoadRefreshTokenPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.SaveRefreshTokenPort;
import momzzangseven.mztkbe.modules.auth.domain.model.RefreshToken;
import org.springframework.stereotype.Component;

/**
 * Validates refresh token through multiple security checks.
 *
 * <p>Single Responsibility: Validation only
 * <p>Used by ReissueTokenService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenValidator {

    private final JwtTokenProvider jwtTokenProvider;
    private final LoadRefreshTokenPort loadRefreshTokenPort;
    private final SaveRefreshTokenPort saveRefreshTokenPort;

    /**
     * Validate JWT format and signature.
     *
     * @param tokenValue JWT token string
     * @throws RefreshTokenNotFoundException if invalid
     */
    public void validateJwtFormat(String tokenValue) {
        if (!jwtTokenProvider.validateToken(tokenValue)) {
            log.warn("Invalid JWT token format or signature");
            throw new RefreshTokenNotFoundException();
        }

        if (!jwtTokenProvider.isRefreshToken(tokenValue)) {
            log.warn("Token is not a refresh token");
            throw new RefreshTokenNotFoundException();
        }

        log.debug("JWT format validation passed");
    }

    /**
     * Load and validate refresh token from database.
     *
     * @param tokenValue Token string to find
     * @return Validated RefreshToken domain model
     * @throws RefreshTokenNotFoundException if not found
     */
    public RefreshToken loadAndValidate(String tokenValue) {
        RefreshToken token = loadRefreshTokenPort
                .findByTokenValue(tokenValue)
                .orElseThrow(() -> {
                    log.error("Refresh token not found in database");
                    return new RefreshTokenNotFoundException();
                });

        log.debug("Token loaded from database: {}", token);
        return token;
    }

    /**
     * Validate userId consistency between JWT and DB.
     *
     * @param jwtUserId UserId from JWT claim
     * @param refreshToken RefreshToken from DB
     * @throws SecurityException if mismatch
     */
    public void validateUserIdConsistency(Long jwtUserId, RefreshToken refreshToken) {
        if (!refreshToken.getUserId().equals(jwtUserId)) {
            log.error("SECURITY: Token userId mismatch! JWT={}, DB={}",
                    jwtUserId, refreshToken.getUserId());
            throw new SecurityException("Token userId mismatch");
        }

        log.debug("UserId consistency validated");

        // revoke suspicious token
        refreshToken.revoke();
        saveRefreshTokenPort.save(refreshToken);

        throw new TokenSecurityException();
    }

    /**
     * Validate domain business rules (expiration, revocation).
     *
     * @param refreshToken RefreshToken to validate
     * @throws RefreshTokenExpiredException if expired
     * @throws RefreshTokenRevokedException if revoked
     */
    public void validateDomainRules(RefreshToken refreshToken) {
        if (!refreshToken.isValid()) {
            if (refreshToken.isExpired()) {
                log.warn("Refresh token expired: userId={}", refreshToken.getUserId());
                throw new RefreshTokenExpiredException();
            }
            if (refreshToken.isRevoked()) {
                log.warn("Refresh token revoked: userId={}", refreshToken.getUserId());
                throw new RefreshTokenRevokedException();
            }
        }

        log.debug("Domain rules validation passed");
    }
}