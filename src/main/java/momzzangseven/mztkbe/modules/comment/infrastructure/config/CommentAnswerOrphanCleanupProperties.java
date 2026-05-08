package momzzangseven.mztkbe.modules.comment.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for orphan answer comment cleanup.
 *
 * <p>Bound from the {@code comment.answer-orphan-cleanup} prefix in application.yml.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "comment.answer-orphan-cleanup")
public class CommentAnswerOrphanCleanupProperties {

  /** Maximum number of orphan answer comments processed per batch iteration. */
  @Min(1)
  private int batchSize = 100;
}
