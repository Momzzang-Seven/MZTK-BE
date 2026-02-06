package momzzangseven.mztkbe.modules.location.application.dto;

/** DTO for GeoCoding API response */
public record CoordinatesInfo(double latitude, double longitude) {
  public static CoordinatesInfo of(double latitude, double longitude) {
    return new CoordinatesInfo(latitude, longitude);
  }
}
