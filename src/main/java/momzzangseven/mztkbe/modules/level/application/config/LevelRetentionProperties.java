package momzzangseven.mztkbe.modules.level.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Retention policy for level data in live tables. */
@Getter
@Setter
@ConfigurationProperties(prefix = "level.retention")
public class LevelRetentionProperties {

  /** How long to keep level data in live tables (days). */
  private int retentionDays;

  /** Batch size for purge job. */
  private int batchSize;

  /** Cron expression for purge job. */
  private String cron;

  /** Time zone for purge job. */
  private String zone;
}
