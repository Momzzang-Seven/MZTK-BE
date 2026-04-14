package momzzangseven.mztkbe.modules.account.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "auth.web-client")
public class AuthWebClientProperties {
  private int connectTimeoutMillis;
  private int responseTimeoutSeconds;
  private int blockTimeoutSeconds;
}
