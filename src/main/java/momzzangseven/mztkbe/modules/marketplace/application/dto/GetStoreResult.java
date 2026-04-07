package momzzangseven.mztkbe.modules.marketplace.application.dto;

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
 * @param xProfileUrl X (Twitter) profile URL
 */
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
    String xProfileUrl) {

  /**
   * Create from TrainerStore domain model.
   *
   * @param store TrainerStore domain model
   * @return GetStoreResult
   */
  public static GetStoreResult from(TrainerStore store) {
    return new GetStoreResult(
        store.getId(),
        store.getStoreName(),
        store.getAddress(),
        store.getDetailAddress(),
        store.getLatitude(),
        store.getLongitude(),
        store.getPhoneNumber(),
        store.getHomepageUrl(),
        store.getInstagramUrl(),
        store.getXProfileUrl());
  }
}
