package momzzangseven.mztkbe.modules.image.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the PENDING image record cleanup job.
 *
 * <p>Bound from the {@code image.pending-cleanup} prefix in application.yml.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "image.pending-cleanup")
public class ImagePendingCleanupProperties {
  /** Number of hours after which a PENDING image is considered orphaned. Defaults to 5 hours. */
  private int retentionHours = 5;

  /** Maximum number of records deleted per batch iteration. Defaults to 100. */
  private int batchSize = 100;
}
