package momzzangseven.mztkbe.modules.account.application.port.out;

import momzzangseven.mztkbe.modules.account.domain.model.RefreshToken;

/**
 * Output Port for persisting refresh tokens.
 *
 * <p>- Application layer defines this interface - Infrastructure layer implements this -
 * Encapsulates persistence operations
 *
 * <p>Responsibility: - Save (create or update) refresh tokens - Delete refresh tokens
 */
public interface SaveRefreshTokenPort {
  /**
   * Save refresh token (create or update).
   *
   * <p>Business Rules: - If token.id is null, creates new record - If token.id exists, updates
   * existing record - Automatically updates timestamps
   *
   * @param refreshToken Domain model to save
   * @return Saved RefreshToken with ID(newly generated or existing)
   */
  RefreshToken save(RefreshToken refreshToken);
}
