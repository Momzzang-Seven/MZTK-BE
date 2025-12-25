package momzzangseven.mztkbe.modules.auth.application.port.out;

import momzzangseven.mztkbe.modules.auth.application.dto.GoogleUserInfo;

public interface GoogleAuthPort {
  String getAccessToken(String authorizationCode);

  GoogleUserInfo getUserInfo(String accessToken);
}
