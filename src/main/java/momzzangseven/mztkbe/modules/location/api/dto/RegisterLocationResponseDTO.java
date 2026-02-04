package momzzangseven.mztkbe.modules.location.api.dto;

import java.time.Instant;
import lombok.Builder;
import momzzangseven.mztkbe.modules.location.application.dto.RegisterLocationResult;

/**
 * Register Location Response DTO
 *
 * @param locationId
 * @param userId
 * @param locationName
 * @param postalCode
 * @param address
 * @param detailAddress
 * @param latitude
 * @param longitude
 * @param registeredAt
 */
@Builder
public record RegisterLocationResponseDTO(
    Long locationId,
    Long userId,
    String locationName,
    String postalCode,
    String address,
    String detailAddress,
    Double latitude,
    Double longitude,
    Instant registeredAt) {
  public static RegisterLocationResponseDTO from(RegisterLocationResult result) {
    return RegisterLocationResponseDTO.builder()
        .locationId(result.locationId())
        .userId(result.userId())
        .locationName(result.locationName())
        .postalCode(result.postalCode())
        .address(result.address())
        .detailAddress(result.detailAddress())
        .latitude(result.latitude())
        .longitude(result.longitude())
        .registeredAt(result.registeredAt())
        .build();
  }
}
