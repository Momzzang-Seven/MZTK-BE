package momzzangseven.mztkbe.modules.account.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadExternalDisconnectCleanupPolicyPort;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cleanup policy for {@code external_disconnect_tasks}.
 *
 * <p>Cleanup is configurable and can run earlier than hard-delete retention.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "withdrawal.external-disconnect.cleanup")
public class WithdrawalExternalDisconnectCleanupProperties
    implements LoadExternalDisconnectCleanupPolicyPort {

  /** Fixed delay between cleanup runs (milliseconds). */
  private long fixedDelay;

  /** Retention for SUCCESS rows (days). Set lower than hard-delete retention to cleanup early. */
  private int successRetentionDays;

  /**
   * Retention for FAILED rows (days).
   *
   * <p>Set to 0 to keep FAILED long-term (until hard delete).
   */
  private int failedRetentionDays;
}
