package momzzangseven.mztkbe.modules.auth.application.dto;

import lombok.Builder;
import momzzangseven.mztkbe.modules.user.domain.model.User;

/**
 * Result of login use case execution.
 *
 * <p>Contains all information needed for API response.
 */
@Builder
public record LoginResult(

    /** JWT access token */
    String accessToken,

    /** JWT refresh token */
    String refreshToken,

    /** Token type (usually "Bearer") */
    String grantType,

    /** Access token expiration time in milliseconds */
    Long accessTokenExpiresIn,

    /** Refresh token expiration time in milliseconds */
    Long refreshTokenExpiresIn,

    /** Whether this is a newly registered user */
    Boolean isNewUser,

    /** Authenticated user information */
    User user) {

  /**
   * Create LoginResult with standard configuration.
   *
   * @param accessToken JWT access token
   * @param refreshToken JWT refresh token
   * @param accessTokenExpiresIn Access token expiration in milliseconds
   * @param refreshTokenExpiresIn Refresh token expiration in milliseconds
   * @param isNewUser Whether user is newly registered
   * @param user User information
   * @return Fully configured LoginResult
   */
  public static LoginResult of(
      String accessToken,
      String refreshToken,
      Long accessTokenExpiresIn,
      Long refreshTokenExpiresIn,
      Boolean isNewUser,
      User user) {

    return LoginResult.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .grantType("Bearer")
        .accessTokenExpiresIn(accessTokenExpiresIn)
        .refreshTokenExpiresIn(refreshTokenExpiresIn)
        .isNewUser(isNewUser)
        .user(user)
        .build();
  }
}
