package momzzangseven.mztkbe.modules.location.domain.vo;

import momzzangseven.mztkbe.global.error.location.InvalidGpsCoordinateException;

/**
 * GPS Coordinate Value Object
 *
 * <p>Immutable Value Object representing GPS coordinate.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Validation on creation (Latitude: -90 to 90, Longitude: -180 to 180)
 *   <li>Distance calculation using Haversine formula
 *   <li>Immutable
 * </ul>
 *
 * @param latitude Latitude (Latitude: -90 to 90)
 * @param longitude Longitude (Longitude: -180 to 180)
 */
public record GpsCoordinate(double latitude, double longitude) {

  /** Earth radius (meters) - used in Haversine formula */
  private static final double EARTH_RADIUS_METERS = 6371000.0;

  /**
   * Compact Constructor - Validation
   *
   * @throws InvalidGpsCoordinateException Latitude or longitude is out of valid range
   */
  public GpsCoordinate {
    validateLatitude(latitude);
    validateLongitude(longitude);
  }

  /**
   * Calculate distance between two GPS coordinates using Haversine formula
   *
   * @param other Comparison target GPS coordinate
   * @return Distance between two coordinates (meters)
   */
  public double distanceTo(GpsCoordinate other) {
    // Convert latitude/longitude difference to radians
    double dLat = Math.toRadians(other.latitude - this.latitude);
    double dLon = Math.toRadians(other.longitude - this.longitude);

    // Apply Haversine formula
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(this.latitude))
                * Math.cos(Math.toRadians(other.latitude))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);

    // Calculate central angle
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    // Distance = Earth radius × central angle
    return EARTH_RADIUS_METERS * c;
  }

  /**
   * Validation of latitude
   *
   * @param lat Latitude value
   * @throws InvalidGpsCoordinateException Latitude is out of valid range
   */
  private static void validateLatitude(double lat) {
    if (lat < -90.0 || lat > 90.0) {
      throw new InvalidGpsCoordinateException(
          String.format("Latitude must be between -90 and 90, but was: %.7f", lat));
    }
  }

  /**
   * Validation of longitude
   *
   * @param lng Longitude value
   * @throws InvalidGpsCoordinateException Longitude is out of valid range
   */
  private static void validateLongitude(double lng) {
    if (lng < -180.0 || lng > 180.0) {
      throw new InvalidGpsCoordinateException(
          String.format("Longitude must be between -180 and 180, but was: %.7f", lng));
    }
  }
}
