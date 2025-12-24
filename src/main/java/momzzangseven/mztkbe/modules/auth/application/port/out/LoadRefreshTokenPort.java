package momzzangseven.mztkbe.modules.auth.application.port.out;

import momzzangseven.mztkbe.modules.auth.domain.model.RefreshToken;

import java.util.Optional;

/**
 * Output Port for loading refresh tokens.
 *
 * - Application layer defines this interface
 * - Infrastructure layer implements this port
 * - Allows application to be independent of persistence details
 *
 * <p>Responsibility:
 * - Query refresh tokens from persistence storage
 * - Return domain models (not entities)
 */
public interface LoadRefreshTokenPort {
    /**
     * Find refresh token by token value.
     *
     * @param tokenValue JWT token string
     * @return Optional of RefreshToken domain model
     */
    Optional<RefreshToken> findByTokenValue(String tokenValue);

    /**
     * Find refresh token by user ID.
     *
     * <p>Note: Returns the most recent token if multiple exist.
     *
     * @param userId User's unique identifier
     * @return Optional of RefreshToken domain model
     */
    Optional<RefreshToken> findByUserId(Long userId);

    /**
     * Check if refresh token exists by token value.
     *
     * @param tokenValue JWT token string
     * @return true if token exists, false otherwise
     */
    boolean existsByTokenValue(String tokenValue);
}
