package momzzangseven.mztkbe.modules.auth.application.delegation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.auth.application.port.out.SaveRefreshTokenPort;
import momzzangseven.mztkbe.modules.auth.domain.model.RefreshToken;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Handles token rotation (issuing new tokens and revoking old ones).
 *
 * <p>Single Responsibility: Token rotation
 * <p>Security: Implements OAuth 2.0 token rotation best practice
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RefreshTokenRotator {
    private final JwtTokenProvider jwtTokenProvider;
    private final SaveRefreshTokenPort saveRefreshTokenPort;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    /**
     * Result of token rotation.
     */
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
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
        log.debug("New access token generated");

        // 2. Generate new refresh token
        String newRefreshTokenValue = jwtTokenProvider.generateRefreshToken(user.getId());
        log.debug("New refresh token generated");

        // 3. Create new refresh token domain model
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(refreshTokenExpiration);
        RefreshToken newRefreshToken = RefreshToken.create(
                user.getId(),
                newRefreshTokenValue,
                expiresAt
        );

        // 4. Save new refresh token
        saveRefreshTokenPort.save(newRefreshToken);
        log.debug("New refresh token saved to database");

        // 5. Revoke old refresh token (Security: Token Rotation)
        oldRefreshToken.revoke();
        saveRefreshTokenPort.save(oldRefreshToken);
        log.info("Token rotation completed. Old token revoked.");

        return new TokenPair(newAccessToken, newRefreshTokenValue);
    }
}
