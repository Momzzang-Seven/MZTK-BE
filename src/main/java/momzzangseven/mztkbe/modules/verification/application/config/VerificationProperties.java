package momzzangseven.mztkbe.modules.verification.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for synchronous workout verification analysis. */
@Getter
@Setter
@ConfigurationProperties(prefix = "verification")
public class VerificationProperties {

  private final Analysis analysis = new Analysis();

  /** Analysis-related thresholds. */
  @Getter
  @Setter
  public static class Analysis {
    private double workoutPhotoConfidenceThreshold = 0.70;
  }
}
