package momzzangseven.mztkbe.modules.auth.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.auth.domain.model.RefreshToken;

/**
 * Output Port for loading refresh tokens.
 *
 * <p>- Application layer defines this interface - Infrastructure layer implements this port -
 * Allows application to be independent of persistence details
 *
 * <p>Responsibility: - Query refresh tokens from persistence storage - Return domain models (not
 * entities)
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
   * Find and lock refresh token for update.
   *
   * @param tokenValue Token value to find
   * @return Locked RefreshToken
   */
  Optional<RefreshToken> findByTokenValueWithLock(String tokenValue);

  /**
   * Check if refresh token exists by token value.
   *
   * @param tokenValue JWT token string
   * @return true if token exists, false otherwise
   */
  boolean existsByTokenValue(String tokenValue);
}
