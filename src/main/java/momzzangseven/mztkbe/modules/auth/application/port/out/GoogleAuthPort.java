package momzzangseven.mztkbe.modules.auth.application.port.out;

import momzzangseven.mztkbe.modules.auth.application.dto.GoogleOAuthToken;
import momzzangseven.mztkbe.modules.auth.application.dto.GoogleUserInfo;

/**
 * Outbound port for Google authentication operations.
 *
 * <p>Implemented by infrastructure layer (GoogleApiAdapter).
 */
public interface GoogleAuthPort {
  GoogleOAuthToken exchangeToken(String authorizationCode);

  String getAccessToken(String authorizationCode);

  GoogleUserInfo getUserInfo(String accessToken);
}
