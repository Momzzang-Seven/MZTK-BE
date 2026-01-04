package momzzangseven.mztkbe.modules.auth.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.auth.application.delegation.RefreshTokenManager;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.stereotype.Component;

/**
 * Issues access/refresh tokens for an authenticated user and returns a {@link LoginResult}.
 *
 * <p>This is shared by multiple auth flows (login, reactivation) to avoid duplicating token
 * issuance logic.
 */
@Component
@RequiredArgsConstructor
public class AuthTokenIssuer {

  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenManager refreshTokenManager;

  /**
   * Issue access/refresh tokens for the given user.
   *
   * @param user authenticated user
   * @param isNewUser whether this login created a new user
   * @return login result containing tokens and user payload
   */
  public LoginResult issue(User user, boolean isNewUser) {
    String accessToken =
        jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
    String refreshToken = refreshTokenManager.createAndSaveRefreshToken(user.getId());

    return LoginResult.of(
        accessToken,
        refreshToken,
        jwtTokenProvider.getAccessTokenExpiresIn(),
        jwtTokenProvider.getRefreshTokenExpiresIn(),
        isNewUser,
        user);
  }
}
