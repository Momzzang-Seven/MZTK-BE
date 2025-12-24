package momzzangseven.mztkbe.modules.auth.application.port.in;

import momzzangseven.mztkbe.modules.auth.application.dto.ReissueTokenCommand;
import momzzangseven.mztkbe.modules.auth.application.dto.ReissueTokenResult;

/**
 * Token Reissue Use Case (Input Port).
 *
 * - API layer depends on this interface
 * - Implemented by ReissueTokenService
 *
 * <p>Responsibility:
 * - Accept refresh token from client
 * - Validate and issue new tokens
 */
public interface ReissueTokenUseCase {
    /**
     * Reissue access token and refresh token.
     *
     * @param command Reissue token command containing refresh token
     * @return ReissueTokenResult with new tokens
     * @throws RefreshTokenNotFoundException if token not found in DB
     * @throws RefreshTokenExpiredException if token is expired
     * @throws RefreshTokenRevokedException if token is revoked
     * @throws InvalidJwtTokenException if JWT validation fails
     */
    public ReissueTokenResult execute(ReissueTokenCommand command);
}
