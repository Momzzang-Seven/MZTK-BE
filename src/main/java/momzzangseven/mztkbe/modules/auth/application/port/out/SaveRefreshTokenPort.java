package momzzangseven.mztkbe.modules.auth.application.port.out;

import momzzangseven.mztkbe.modules.auth.domain.model.RefreshToken;

/**
 * Output Port for persisting refresh tokens.
 *
 * - Application layer defines this interface
 * - Infrastructure layer implements this
 * - Encapsulates persistence operations
 *
 * <p>Responsibility:
 * - Save (create or update) refresh tokens
 * - Delete refresh tokens
 */
public interface SaveRefreshTokenPort {
    /**
     * Save refresh token (create or update).
     *
     * <p>Business Rules:
     * - If token.id is null, creates new record
     * - If token.id exists, updates existing record
     * - Automatically updates timestamps
     *
     * @param refreshToken Domain model to save
     * @return Saved RefreshToken with ID(newly generated or existing)
     */
    RefreshToken save(RefreshToken refreshToken);

    /**
     * Delete refresh token.
     *
     * @param refreshToken Domain model to delete
     */
    void delete(RefreshToken refreshToken);

    /**
     * Delete all refresh tokens for a user.
     *
     * <p>Use case: User logout - invalidate all tokens
     *
     * @param userId User's unique identifier
     */
    void deleteByUserId(Long userId);
}
