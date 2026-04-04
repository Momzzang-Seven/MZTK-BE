package momzzangseven.mztkbe.modules.account.application.port.out;

import momzzangseven.mztkbe.modules.account.application.dto.GoogleOAuthToken;
import momzzangseven.mztkbe.modules.account.application.dto.GoogleUserInfo;

/**
 * Outbound port for Google authentication operations.
 *
 * <p>Implemented by infrastructure layer (GoogleApiAdapter).
 */
public interface GoogleAuthPort {
  GoogleOAuthToken exchangeToken(String authorizationCode);

  String getAccessToken(String authorizationCode);

  GoogleUserInfo getUserInfo(String accessToken);

  /**
   * Revoke a Google OAuth refresh token to disconnect the user.
   *
   * @param refreshToken refresh token
   */
  void revokeRefreshToken(String refreshToken);

  /**
   * Revoke a Google OAuth access token to disconnect the user.
   *
   * <p>Note: For withdrawal/disconnect flows, refresh token is preferred.
   *
   * @param accessToken access token
   */
  void revokeAccessToken(String accessToken);
}
