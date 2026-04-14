package momzzangseven.mztkbe.modules.account.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.account.application.delegation.RefreshTokenManager;
import momzzangseven.mztkbe.modules.account.application.dto.IssuedTokens;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.stereotype.Component;

/**
 * Issues JWT access/refresh tokens for an authenticated user.
 *
 * <p>Shared by multiple auth flows (login, reactivation) to avoid duplicating token issuance logic.
 * Callers are responsible for assembling the final {@code LoginResult}.
 */
@Component
@RequiredArgsConstructor
public class AuthTokenIssuer {

  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenManager refreshTokenManager;

  /**
   * Generate and persist an access/refresh token pair for the given user identity.
   *
   * @param userId user's numeric ID
   * @param email user's email address
   * @param role user's role as String (e.g. "USER", "ADMIN")
   * @return issued token data
   */
  public IssuedTokens issueTokens(Long userId, String email, String role) {
    String accessToken =
        jwtTokenProvider.generateAccessToken(userId, email, UserRole.valueOf(role));
    String refreshToken = refreshTokenManager.createAndSaveRefreshToken(userId);
    return new IssuedTokens(
        accessToken,
        refreshToken,
        "Bearer",
        jwtTokenProvider.getAccessTokenExpiresIn(),
        jwtTokenProvider.getRefreshTokenExpiresIn());
  }
}
