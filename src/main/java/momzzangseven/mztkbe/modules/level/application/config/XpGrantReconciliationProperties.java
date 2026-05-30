package momzzangseven.mztkbe.modules.level.application.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configuration for the XP-grant outbox reconciliation scheduler. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "level.xp-reconciliation")
public class XpGrantReconciliationProperties {

  /** Whether the scheduler is active. Disabled by default (and in tests). */
  private boolean enabled = false;

  @NotBlank private String cron = "0 */5 * * * *";

  @NotBlank private String zone = "Asia/Seoul";

  @Min(1)
  @Max(1_000)
  private int batchSize = 100;

  @Min(1)
  @Max(100)
  private int maxAttempts = 10;

  @Min(1)
  @Max(86_400)
  private int backoffSeconds = 60;
}
