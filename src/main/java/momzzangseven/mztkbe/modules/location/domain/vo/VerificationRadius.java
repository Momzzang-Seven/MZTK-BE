package momzzangseven.mztkbe.modules.location.domain.vo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Verification Radius Configuration
 *
 * <p>Verification radius configuration. Managed in application.yml and can be flexibly adapted to
 * planning changes.
 *
 * <p>Configuration location: {@code location.verification.radius-meters}
 *
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "location.verification")
public class VerificationRadius {

  /** Verification radius (meters) */
  private double radiusMeters;

  /**
   * Check if the distance is within the verification radius
   *
   * @param distance distance (meters)
   * @return true if within radius
   * @throws IllegalArgumentException if distance is negative
   */
  public boolean isWithin(double distance) {
    if (distance < 0) {
      throw new IllegalArgumentException("Distance cannot be negative");
    }
    return distance <= radiusMeters;
  }
}
