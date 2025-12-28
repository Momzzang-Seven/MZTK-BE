package momzzangseven.mztkbe.modules.auth.application.dto;

import lombok.Builder;
import lombok.Getter;

/** User information extracted from Google OpenID Connect profile. */
@Getter
@Builder
public class GoogleUserInfo {
  private String providerUserId;
  private String email;
  private String nickname;
  private String profileImageUrl;
}
