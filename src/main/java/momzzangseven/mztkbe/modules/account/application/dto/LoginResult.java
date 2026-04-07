package momzzangseven.mztkbe.modules.account.application.dto;

import lombok.Builder;

/**
 * Result of login use case execution.
 *
 * <p>Contains all information needed for API response.
 *
 * @param accessToken JWT access token
 * @param refreshToken JWT refresh token
 * @param grantType Token type (usually "Bearer")
 * @param accessTokenExpiresIn Access token expiration time in milliseconds
 * @param refreshTokenExpiresIn Refresh token expiration time in milliseconds
 * @param isNewUser Whether this is a newly registered user
 * @param userSnapshot Authenticated user profile snapshot
 * @param walletAddress Active wallet address from user_wallets, or null if not registered
 */
@Builder
public record LoginResult(
    String accessToken,
    String refreshToken,
    String grantType,
    Long accessTokenExpiresIn,
    Long refreshTokenExpiresIn,
    Boolean isNewUser,
    AccountUserSnapshot userSnapshot,
    String walletAddress) {

  /**
   * Create LoginResult with standard configuration.
   *
   * @param accessToken JWT access token
   * @param refreshToken JWT refresh token
   * @param accessTokenExpiresIn Access token expiration in milliseconds
   * @param refreshTokenExpiresIn Refresh token expiration in milliseconds
   * @param isNewUser Whether user is newly registered
   * @param userSnapshot User profile snapshot
   * @param walletAddress Active wallet address, or null
   * @return Fully configured LoginResult
   */
  public static LoginResult of(
      String accessToken,
      String refreshToken,
      Long accessTokenExpiresIn,
      Long refreshTokenExpiresIn,
      Boolean isNewUser,
      AccountUserSnapshot userSnapshot,
      String walletAddress) {

    return LoginResult.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .grantType("Bearer")
        .accessTokenExpiresIn(accessTokenExpiresIn)
        .refreshTokenExpiresIn(refreshTokenExpiresIn)
        .isNewUser(isNewUser)
        .userSnapshot(userSnapshot)
        .walletAddress(walletAddress)
        .build();
  }

  /**
   * Convenience factory that assembles a LoginResult from the three inputs produced during the auth
   * flow.
   *
   * @param tokens token pair from {@code AuthTokenIssuer}
   * @param auth authenticated user and new-user flag
   * @param walletAddress active wallet address, or null
   * @return fully configured LoginResult
   */
  public static LoginResult of(IssuedTokens tokens, AuthenticatedUser auth, String walletAddress) {
    return of(
        tokens.accessToken(),
        tokens.refreshToken(),
        tokens.accessTokenExpiresIn(),
        tokens.refreshTokenExpiresIn(),
        auth.isNewUser(),
        auth.userSnapshot(),
        walletAddress);
  }
}
