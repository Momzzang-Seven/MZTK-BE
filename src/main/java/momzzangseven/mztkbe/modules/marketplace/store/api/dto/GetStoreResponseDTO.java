package momzzangseven.mztkbe.modules.marketplace.store.api.dto;

import momzzangseven.mztkbe.modules.marketplace.store.application.dto.GetStoreResult;

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
 * @param xProfileUrl X (Twitter) profile URL
 */
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
    String xProfileUrl) {

  /**
   * Create from GetStoreResult.
   *
   * @param result application layer result
   * @return GetStoreResponseDTO
   */
  public static GetStoreResponseDTO from(GetStoreResult result) {
    return new GetStoreResponseDTO(
        result.storeId(),
        result.storeName(),
        result.address(),
        result.detailAddress(),
        result.latitude(),
        result.longitude(),
        result.phoneNumber(),
        result.homepageUrl(),
        result.instagramUrl(),
        result.xProfileUrl());
  }
}
