package momzzangseven.mztkbe.modules.auth.infrastructure.kakao;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "kakao")
public class KakaoAuthProperties {

  private Auth auth = new Auth();
  private Api api = new Api();

  /** Kakao OAuth client configuration values. */
  @Getter
  @Setter
  public static class Auth {
    private String client; // REST API Key
    private String redirect; // redirect_uri
  }

  /** Kakao external API endpoints that we call. */
  @Getter
  @Setter
  public static class Api {
    private String tokenUri;
    private String userinfoUri;
  }
}
