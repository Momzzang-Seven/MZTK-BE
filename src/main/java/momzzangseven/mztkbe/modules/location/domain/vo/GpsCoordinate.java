package momzzangseven.mztkbe.modules.location.domain.vo;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.location.InvalidGpsCoordinateException;

/**
 * Value Object for Gps coordinate. - Immutable - Check validity when creation - Calculate distance
 * between two Gps coordinates using Haversine formula
 */
@Getter
@EqualsAndHashCode
public class GpsCoordinate {
  private final double latitude; // 위도
  private final double longitude; // 경도

  public GpsCoordinate(double latitude, double longitude) {
    validateLatitude(latitude);
    validateLongitude(longitude);
    this.latitude = latitude;
    this.longitude = longitude;
  }

  private void validateLatitude(double lat) {
    if (lat < -90.0 || lat > 90.0) {
      throw new InvalidGpsCoordinateException(
          String.format("Latitude must be between -90 and 90, but was: %.7f", lat));
    }
  }

  private void validateLongitude(double lng) {
    if (lng < -180.0 || lng > 180.0) {
      throw new InvalidGpsCoordinateException(
          String.format("Longitude must be between -180 and 180, but was: %.7f", lng));
    }
  }

  /**
   * Calculate distance between two GPS coordinate using Haversine
   *
   * @param other comparison target GPS Coordinate value object
   * @return distance (in meter)
   */
  public double distanceTo(GpsCoordinate other) {
    final double EARTH_RADIUS_METERS = 6371000.0;

    double dLat = Math.toRadians(other.latitude - this.latitude);
    double dLon = Math.toRadians(other.longitude - this.longitude);

    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(this.latitude))
                * Math.cos(Math.toRadians(other.latitude))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);

    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return EARTH_RADIUS_METERS * c;
  }

  @Override
  public String toString() {
    return String.format("GpsCoordinate(lat=%.7f, lng=%.7f)", latitude, longitude);
  }
}
