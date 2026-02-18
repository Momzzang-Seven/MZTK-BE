package momzzangseven.mztkbe.modules.location.application.dto;

import lombok.Builder;
import momzzangseven.mztkbe.global.error.location.MissingLocationInfoException;
import momzzangseven.mztkbe.modules.location.api.dto.RegisterLocationRequestDTO;

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
@Builder
public record RegisterLocationCommand(
    Long userId,
    String locationName,
    String postalCode, // nullable (if latitude and longitude is provided)
    String address, // nullable (if latitude and longitude is provided)
    String detailAddress, // nullable(Optional)
    Double latitude, // nullable (if address and postalCode is provided)
    Double longitude // nullable (if address and postalCode is provided)
    ) {

  public static RegisterLocationCommand from(Long userId, RegisterLocationRequestDTO request) {
    return RegisterLocationCommand.builder()
        .userId(userId)
        .locationName(request.locationName())
        .postalCode(request.postalCode())
        .address(request.address())
        .detailAddress(request.detailAddress())
        .latitude(request.latitude())
        .longitude(request.longitude())
        .build();
  }

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
