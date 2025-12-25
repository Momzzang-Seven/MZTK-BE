package momzzangseven.mztkbe.modules.auth.infrastructure.google;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "google")
public class GoogleAuthProperties {

    private OAuth oauth = new OAuth();
    private Api api = new Api();

    @Getter
    @Setter
    public static class OAuth {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
    }

    @Getter
    @Setter
    public static class Api {
        private String tokenUri;
        private String userinfoUri;
    }
}
