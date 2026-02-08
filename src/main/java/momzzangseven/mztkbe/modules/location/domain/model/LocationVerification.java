package momzzangseven.mztkbe.modules.location.domain.model;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;
import momzzangseven.mztkbe.modules.location.domain.vo.VerificationRadius;

/**
 * Location Verification Domain Model It demonstrates the result of location verification. It
 * contains
 *
 * <ul>
 *   <li>verification success/failure
 *   <li>distance
 *   <li>GPS coordinates
 * </ul>
 *
 * Every location verification trial is saved in "location_verifications" table in DB
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LocationVerification {
  private Long id;
  private Long userId;
  private Long locationId; // nullable
  private String locationName; // denormalization: location name at the time of verification
  private boolean isVerified; // verification success/failure
  private double distance; // distance between registered location and current location (meters)
  private GpsCoordinate registeredCoordinate;
  private GpsCoordinate currentCoordinate;
  private Instant verifiedAt;

  /**
   * Factory Method: create location verification result
   *
   * @param userId user id
   * @param location registered location domain model
   * @param currentCoordinate current GPS coordinate
   * @return LocationVerification verification result
   */
  public static LocationVerification create(
      Long userId,
      Location location,
      GpsCoordinate currentCoordinate,
      VerificationRadius verificationRadius) {
    // calculate distance using Haversine formula
    double distance = location.calculateDistanceFrom(currentCoordinate);

    // verification decision (within 5m = pass)
    boolean isVerified = verificationRadius.isWithin(distance);

    return LocationVerification.builder()
        .userId(userId)
        .locationId(location.getId())
        .locationName(
            location
                .getLocationName()) // denormalization: location name at the time of verification
        .isVerified(isVerified)
        .distance(distance)
        .registeredCoordinate(location.getCoordinate())
        .currentCoordinate(currentCoordinate)
        .verifiedAt(Instant.now())
        .build();
  }

  /** business logic: verification success/failure */
  public boolean isSuccessful() {
    return this.isVerified;
  }
}
