package momzzangseven.mztkbe.global.security;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "security.cors")
public class SecurityCorsProperties {
  private List<String> allowedOrigins;
}
