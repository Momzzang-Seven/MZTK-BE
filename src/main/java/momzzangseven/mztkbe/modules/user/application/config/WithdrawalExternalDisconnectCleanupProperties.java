package momzzangseven.mztkbe.modules.user.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cleanup policy for {@code external_disconnect_tasks}.
 *
 * <p>Cleanup is configurable and can run earlier than hard-delete retention.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "withdrawal.external-disconnect.cleanup")
public class WithdrawalExternalDisconnectCleanupProperties {

  /** Fixed delay between cleanup runs (milliseconds). */
  private long fixedDelay = 86_400_000; // 1 day

  /** Retention for SUCCESS rows (days). Set lower than hard-delete retention to cleanup early. */
  private int successRetentionDays = 7;

  /**
   * Retention for FAILED rows (days).
   *
   * <p>Set to 0 to keep FAILED long-term (until hard delete).
   */
  private int failedRetentionDays = 0;
}
