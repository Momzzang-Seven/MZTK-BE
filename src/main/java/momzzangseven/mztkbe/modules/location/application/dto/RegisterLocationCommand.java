package momzzangseven.mztkbe.modules.location.application.dto;

import momzzangseven.mztkbe.global.error.location.MissingLocationInfoException;

/**
 * Command for Location Registration Input parameter of Service
 *
 * @param userId
 * @param locationName
 * @param postalCode
 * @param address
 * @param detailAddress
 * @param latitude
 * @param longitude
 */
public record RegisterLocationCommand(
    Long userId,
    String locationName,
    String postalCode, // nullable (if latitude and longitude is provided)
    String address, // nullable (if latitude and longitude is provided)
    String detailAddress, // nullable(Optional)
    Double latitude, // nullable (if address and postalCode is provided)
    Double longitude // nullable (if address and postalCode is provided)
    ) {

  /** Validate the input command Either address or GPS coordinates must be provided */
  public void validate() {
    if (!hasAddressInfo() && !hasCoordinatesInfo()) {
      throw new MissingLocationInfoException("Either address or GPS coordinates must be provided");
    }
  }

  public boolean hasAddressInfo() {
    return address != null && !address.isBlank() && postalCode != null && !postalCode.isBlank();
  }

  public boolean hasCoordinatesInfo() {
    return latitude != null && longitude != null;
  }
}
