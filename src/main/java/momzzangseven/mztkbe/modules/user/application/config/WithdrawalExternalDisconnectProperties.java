package momzzangseven.mztkbe.modules.user.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for social withdrawal external disconnect retry. */
@Getter
@Setter
@ConfigurationProperties(prefix = "withdrawal.external-disconnect")
public class WithdrawalExternalDisconnectProperties {

  /** Fixed delay between scheduler runs (milliseconds). */
  private long fixedDelay = 60000;

  /** Max number of tasks to process per run. */
  private int batchSize = 20;

  /** Maximum total attempts (including the immediate attempt on withdrawal). */
  private int maxAttempts = 10;

  /** Initial backoff after first failure (milliseconds). */
  private long initialBackoff = 300000;

  /** Maximum backoff between retries (milliseconds). */
  private long maxBackoff = 3600000;
}
