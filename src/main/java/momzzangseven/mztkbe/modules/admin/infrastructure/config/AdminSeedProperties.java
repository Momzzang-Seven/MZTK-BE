package momzzangseven.mztkbe.modules.admin.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadSeedPolicyPort;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for seed admin provisioning.
 *
 * <p>Bound from the {@code mztk.admin.seed} prefix in application.yml.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "mztk.admin.seed")
public class AdminSeedProperties implements LoadSeedPolicyPort {

  /** Number of seed admin accounts to provision. Defaults to 2. */
  private int seedCount = 2;

  /** AWS Secrets Manager secret ID for delivering bootstrap credentials. */
  private String deliveryTarget = "mztk/admin/bootstrap-delivery";
}
