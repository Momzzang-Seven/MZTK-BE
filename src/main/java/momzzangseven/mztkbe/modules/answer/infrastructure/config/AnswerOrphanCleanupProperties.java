package momzzangseven.mztkbe.modules.answer.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the orphan answer cleanup job.
 *
 * <p>Bound from the {@code answer.orphan-cleanup} prefix in application.yml.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "answer.orphan-cleanup")
public class AnswerOrphanCleanupProperties {

  /** Maximum number of orphan answers processed per batch iteration. Defaults to 100. */
  private int batchSize = 100;
}
