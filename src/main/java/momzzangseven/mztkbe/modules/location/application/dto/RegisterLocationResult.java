package momzzangseven.mztkbe.modules.location.application.dto;

import java.time.Instant;
import lombok.Builder;

/** Result of location registration */
@Builder
public record RegisterLocationResult(
    Long locationId,
    Long userId,
    String locationName,
    String postalCode,
    String address,
    String detailAddress,
    Double latitude,
    Double longitude,
    Instant registeredAt) {
  public static RegisterLocationResult from(LocationItem item, Long userId) {
    return RegisterLocationResult.builder()
        .locationId(item.locationId())
        .userId(userId)
        .locationName(item.locationName())
        .postalCode(item.postalCode())
        .address(item.address())
        .detailAddress(item.detailAddress())
        .latitude(item.latitude())
        .longitude(item.longitude())
        .registeredAt(item.registeredAt())
        .build();
  }
}
