package momzzangseven.mztkbe.modules.account.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadHardDeletePolicyPort;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for withdrawal hard-delete policy. */
@Getter
@Setter
@ConfigurationProperties(prefix = "withdrawal.hard-delete")
public class WithdrawalHardDeleteProperties implements LoadHardDeletePolicyPort {

  /** Retention window in days before hard delete. */
  private int retentionDays;

  /** Batch size for selecting hard-delete targets. */
  private int batchSize;

  /** Cron expression for hard-delete job. */
  private String cron;

  /** Time zone for hard-delete cron. */
  private String zone;
}
