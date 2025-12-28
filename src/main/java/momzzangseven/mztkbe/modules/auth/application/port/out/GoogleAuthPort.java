package momzzangseven.mztkbe.modules.auth.application.port.out;

import momzzangseven.mztkbe.modules.auth.application.dto.GoogleUserInfo;

/**
 * Outbound port for Google authentication operations.
 *
 * <p>Implemented by infrastructure layer (GoogleApiAdapter).
 */
public interface GoogleAuthPort {
  String getAccessToken(String authorizationCode);

  GoogleUserInfo getUserInfo(String accessToken);
}
