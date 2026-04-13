package momzzangseven.mztkbe.modules.admin.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for the recovery anchor in local/test/e2e environments. */
@Getter
@Setter
@ConfigurationProperties(prefix = "mztk.admin.recovery")
public class RecoveryAnchorProperties {

  private String anchor;

  /** AWS Secrets Manager secret ID for the recovery anchor. */
  private String secretId = "mztk/admin/recovery-anchor";
}
