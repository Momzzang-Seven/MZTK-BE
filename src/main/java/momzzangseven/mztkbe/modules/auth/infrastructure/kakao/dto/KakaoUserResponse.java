package momzzangseven.mztkbe.modules.auth.infrastructure.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class KakaoUserResponse {

  private Long id;
  private Properties properties;

  @JsonProperty("kakao_account")
  private KakaoAccount kakaoAccount;

  @Getter
  @ToString
  public static class Properties {
    private String nickname;

    @JsonProperty("profile_image")
    private String profileImage;

    @JsonProperty("thumbnail_image")
    private String thumbnailImage;
  }

  @Getter
  @ToString
  public static class KakaoAccount {
    private String email;
  }
}
