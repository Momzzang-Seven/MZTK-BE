package momzzangseven.mztkbe.modules.user.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for withdrawal hard-delete policy. */
@Getter
@Setter
@ConfigurationProperties(prefix = "withdrawal.hard-delete")
public class WithdrawalHardDeleteProperties {

  /** Retention window in days before hard delete. */
  private int retentionDays = 30;

  /** Batch size for selecting hard-delete targets. */
  private int batchSize = 200;

  /** Cron expression for hard-delete job (used in Phase 5). */
  private String cron;

  /** Time zone for hard-delete cron (used in Phase 5). */
  private String zone;
}
