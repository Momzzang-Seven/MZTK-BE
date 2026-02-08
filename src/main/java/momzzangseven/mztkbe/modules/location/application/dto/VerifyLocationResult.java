package momzzangseven.mztkbe.modules.location.application.dto;

import java.time.Instant;
import momzzangseven.mztkbe.modules.location.domain.model.LocationVerification;

/**
 * Verify Location Result
 *
 * <p>Location verification result DTO. Data to be converted into API response.
 */
public record VerifyLocationResult(
    Long verificationId,
    Long locationId,
    String locationName,
    Long userId,
    boolean isVerified,
    double distance,
    double registeredLatitude,
    double registeredLongitude,
    double currentLatitude,
    double currentLongitude,
    Instant verifiedAt,
    boolean xpGranted,
    int grantedXp,
    String xpGrantMessage) {

  /** Factory Method: Domain Model → DTO conversion (without XP info) */
  public static VerifyLocationResult from(
      LocationVerification verification, XpGrantInfo xpGrantInfo) {
    return new VerifyLocationResult(
        verification.getId(),
        verification.getLocationId(),
        verification.getLocationName(),
        verification.getUserId(),
        verification.isVerified(),
        verification.getDistance(),
        verification.getRegisteredCoordinate().latitude(),
        verification.getRegisteredCoordinate().longitude(),
        verification.getCurrentCoordinate().latitude(),
        verification.getCurrentCoordinate().longitude(),
        verification.getVerifiedAt(),
        xpGrantInfo.granted(),
        xpGrantInfo.amount(),
        xpGrantInfo.message());
  }
}
