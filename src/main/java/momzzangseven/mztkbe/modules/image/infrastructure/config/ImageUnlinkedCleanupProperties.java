package momzzangseven.mztkbe.modules.image.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the unlinked image cleanup job.
 *
 * <p>Bound from the {@code image.unlinked-cleanup} prefix in application.yml.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "image.unlinked-cleanup")
public class ImageUnlinkedCleanupProperties {
  /**
   * Number of hours after which an unlinked image is eligible for permanent deletion. Must be large
   * enough to allow Lambda to complete processing (Lambda max timeout: 15 min). Defaults to 5
   * hours.
   */
  private int retentionHours = 5;

  /** Maximum number of records processed per batch iteration. Defaults to 100. */
  private int batchSize = 100;
}
