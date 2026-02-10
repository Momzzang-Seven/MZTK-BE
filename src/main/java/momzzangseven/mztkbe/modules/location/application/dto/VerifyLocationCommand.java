package momzzangseven.mztkbe.modules.location.application.dto;

import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;

/**
 * Verify Location Command
 *
 * @param userId User ID (verified user)
 * @param locationId Location ID to verify
 * @param currentCoordinate Current GPS coordinate
 */
public record VerifyLocationCommand(Long userId, Long locationId, GpsCoordinate currentCoordinate) {
  /** Compact Constructor - Validation */
  public VerifyLocationCommand {
    if (userId == null) {
      throw new IllegalArgumentException("userId is required");
    }
    if (locationId == null) {
      throw new IllegalArgumentException("locationId is required");
    }
    if (currentCoordinate == null) {
      throw new IllegalArgumentException("currentCoordinate is required");
    }
    // GpsCoordinate performs validation in its own constructor
  }

  /** Factory Method */
  public static VerifyLocationCommand of(
      Long userId, Long locationId, double currentLatitude, double currentLongitude) {
    GpsCoordinate currentCoordinate = new GpsCoordinate(currentLatitude, currentLongitude);
    return new VerifyLocationCommand(userId, locationId, currentCoordinate);
  }
}
