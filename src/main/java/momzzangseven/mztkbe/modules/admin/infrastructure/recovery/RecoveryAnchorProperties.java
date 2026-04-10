package momzzangseven.mztkbe.modules.admin.infrastructure.recovery;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configuration properties for the recovery anchor in local/test/e2e environments. */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "mztk.admin.recovery")
public class RecoveryAnchorProperties {

  private String anchor;
}
