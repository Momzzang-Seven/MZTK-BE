package momzzangseven.mztkbe.modules.auth.application.dto;

import lombok.Builder;
import lombok.Getter;

/** User information extracted from Kakao OAuth profile. */
@Getter
@Builder
public class KakaoUserInfo {
  private String providerUserId;
  private String email;
  private String nickname;
  private String profileImageUrl;
}
