package momzzangseven.mztkbe.modules.image.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the orphan answer image cleanup job.
 *
 * <p>Bound from the {@code image.answer-orphan-cleanup} prefix in application.yml.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "image.answer-orphan-cleanup")
public class ImageAnswerOrphanCleanupProperties {

  /**
   * Maximum number of orphan answer-linked images processed per batch iteration. Defaults to 100.
   */
  private int batchSize = 100;
}
