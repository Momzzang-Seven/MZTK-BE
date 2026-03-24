package momzzangseven.mztkbe.modules.image.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the orphan post image cleanup job.
 *
 * <p>Bound from the {@code image.post-orphan-cleanup} prefix in application.yml.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "image.post-orphan-cleanup")
public class ImagePostOrphanCleanupProperties {

  /** Maximum number of orphan post-linked images processed per batch iteration. Defaults to 100. */
  private int batchSize = 100;
}
