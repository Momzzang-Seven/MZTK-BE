package momzzangseven.mztkbe.modules.marketplace.api.dto;

import lombok.Builder;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetStoreResult;

/**
 * Response DTO for store retrieval.
 *
 * @param storeId store ID
 * @param storeName store name
 * @param address store address
 * @param detailAddress detailed address
 * @param latitude latitude coordinate
 * @param longitude longitude coordinate
 * @param phoneNumber phone number
 * @param homepageUrl homepage URL
 * @param instagramUrl Instagram URL
 * @param xUrl X (Twitter) URL
 */
@Builder
public record GetStoreResponseDTO(
    Long storeId,
    String storeName,
    String address,
    String detailAddress,
    Double latitude,
    Double longitude,
    String phoneNumber,
    String homepageUrl,
    String instagramUrl,
    String xUrl) {

  /**
   * Create from GetStoreResult.
   *
   * @param result application layer result
   * @return GetStoreResponseDTO
   */
  public static GetStoreResponseDTO from(GetStoreResult result) {
    return GetStoreResponseDTO.builder()
        .storeId(result.storeId())
        .storeName(result.storeName())
        .address(result.address())
        .detailAddress(result.detailAddress())
        .latitude(result.latitude())
        .longitude(result.longitude())
        .phoneNumber(result.phoneNumber())
        .homepageUrl(result.homepageUrl())
        .instagramUrl(result.instagramUrl())
        .xUrl(result.xUrl())
        .build();
  }
}
