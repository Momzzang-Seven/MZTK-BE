package momzzangseven.mztkbe.modules.location.application.dto;

/** DTO for GeoCoding API response */
public record GeoCoordinates(double latitude, double longitude) {
  public static GeoCoordinates of(double latitude, double longitude) {
    return new GeoCoordinates(latitude, longitude);
  }
}
