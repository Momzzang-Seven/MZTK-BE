package momzzangseven.mztkbe.modules.auth.application.port.out;

import momzzangseven.mztkbe.modules.auth.application.dto.KakaoUserInfo;

public interface KakaoAuthPort {
  KakaoUserInfo authenticate(String authorizationCode);
}
