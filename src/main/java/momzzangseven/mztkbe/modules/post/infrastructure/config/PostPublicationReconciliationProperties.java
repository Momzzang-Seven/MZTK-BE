package momzzangseven.mztkbe.modules.post.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configuration properties for the post publication reconciliation scheduler. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "post.publication-reconciliation")
public class PostPublicationReconciliationProperties {

  private boolean enabled = false;

  @NotBlank private String cron = "0 */10 * * * *";

  @NotBlank private String zone = "Asia/Seoul";

  @Min(1)
  @Max(1_000)
  private int batchSize = 100;

  private boolean dryRun = true;

  @Min(1)
  @Max(1_000)
  private int maxBatchesPerRun = 10;
}
