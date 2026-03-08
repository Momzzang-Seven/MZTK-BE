package momzzangseven.mztkbe.modules.verification.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for verification analysis, retry, and recovery policies. */
@Getter
@Setter
@ConfigurationProperties(prefix = "verification")
public class VerificationProperties {

  private final Analysis analysis = new Analysis();
  private final Retry retry = new Retry();
  private final Recovery recovery = new Recovery();

  /** Analysis-related thresholds. */
  @Getter
  @Setter
  public static class Analysis {
    private double workoutPhotoConfidenceThreshold = 0.70;
  }

  /** Retry-related limits and backoff values. */
  @Getter
  @Setter
  public static class Retry {
    private int maxAttempts = 3;
    private long firstBackoffMinutes = 1;
    private long secondBackoffMinutes = 5;
    private long thirdBackoffMinutes = 15;
  }

  /** Recovery scanner thresholds for stale requests. */
  @Getter
  @Setter
  public static class Recovery {
    private long pendingStaleMinutes = 5;
    private long analyzingStaleMinutes = 10;
  }
}
