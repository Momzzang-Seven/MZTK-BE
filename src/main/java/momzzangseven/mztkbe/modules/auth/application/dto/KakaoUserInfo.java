package momzzangseven.mztkbe.modules.auth.application.dto;

import lombok.Builder;

@Builder
public class KakaoUserInfo {
  private String providerUserId;
  private String email;
  private String nickname;
  private String profileImageUrl;
}
