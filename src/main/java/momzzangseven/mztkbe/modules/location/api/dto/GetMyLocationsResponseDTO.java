package momzzangseven.mztkbe.modules.location.api.dto;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import momzzangseven.mztkbe.modules.location.application.dto.GetMyLocationsResult;
import momzzangseven.mztkbe.modules.location.application.dto.LocationItem;

/**
 * Get My Locations Response DTO
 *
 * <p>위치 목록 조회 API 응답 DTO
 */
@Builder
public record GetMyLocationsResponseDTO(List<LocationItemDTO> locations, Integer totalCount) {

  /**
   * Factory Method: Application Result → API Response 변환
   *
   * @param result Application layer result
   * @return GetMyLocationsResponseDTO
   */
  public static GetMyLocationsResponseDTO from(GetMyLocationsResult result) {
    List<LocationItemDTO> locationDTOs =
        result.locations().stream().map(LocationItemDTO::from).toList();

    return GetMyLocationsResponseDTO.builder()
        .locations(locationDTOs)
        .totalCount(result.totalCount())
        .build();
  }

  /**
   * Location Item DTO (nested)
   *
   * <p>위치 목록의 개별 항목
   */
  @Builder
  public record LocationItemDTO(
      Long locationId,
      String locationName,
      String postalCode,
      String address,
      String detailAddress,
      Double latitude,
      Double longitude,
      Instant registeredAt) {

    public static LocationItemDTO from(LocationItem item) {
      return LocationItemDTO.builder()
          .locationId(item.locationId())
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
}
