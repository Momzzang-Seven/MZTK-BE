package momzzangseven.mztkbe.modules.marketplace.application.dto;

import lombok.Builder;
import momzzangseven.mztkbe.modules.marketplace.domain.model.TrainerStore;

/**
 * Result of store retrieval operation.
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
public record GetStoreResult(
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
   * Create from TrainerStore domain model.
   *
   * @param store TrainerStore domain model
   * @return GetStoreResult
   */
  public static GetStoreResult from(TrainerStore store) {
    return GetStoreResult.builder()
        .storeId(store.getId())
        .storeName(store.getStoreName())
        .address(store.getAddress())
        .detailAddress(store.getDetailAddress())
        .latitude(store.getLatitude())
        .longitude(store.getLongitude())
        .phoneNumber(store.getPhoneNumber())
        .homepageUrl(store.getHomepageUrl())
        .instagramUrl(store.getInstagramUrl())
        .xUrl(store.getXUrl())
        .build();
  }
}
