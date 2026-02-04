package momzzangseven.mztkbe.modules.location.application.dto;

import java.time.Instant;
import lombok.Builder;
import momzzangseven.mztkbe.modules.location.domain.model.Location;

/**
 * Application layer DTO containing Location information - purpose: Separate application and domain
 * layer
 */
@Builder
public record LocationItem(
    Long locationId,
    String locationName,
    String postalCode,
    String address,
    String detailAddress,
    Double latitude,
    Double longitude,
    Instant registeredAt) {
  public static LocationItem from(Location location) {
    return LocationItem.builder()
        .locationId(location.getId())
        .locationName(location.getLocationName())
        .postalCode(location.getPostalCode())
        .address(location.getAddress())
        .detailAddress(location.getDetailAddress())
        .latitude(location.getCoordinate().getLatitude())
        .longitude(location.getCoordinate().getLongitude())
        .registeredAt(location.getRegisteredAt())
        .build();
  }
}
