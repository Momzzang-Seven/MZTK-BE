package momzzangseven.mztkbe.modules.marketplace.application.dto;

import java.util.List;

/** Result of a class detail query including store info, images, and slots. */
public record GetClassDetailResult(
    Long classId,
    Long trainerId,
    StoreInfo store,
    String title,
    String category,
    String description,
    int priceAmount,
    String thumbnailFinalObjectKey,
    List<ImageInfo> images,
    List<String> tags,
    List<String> features,
    int durationMinutes,
    String personalItems,
    List<ClassSlotInfo> classTimes) {

  /**
   * Embedded store information for the detail response. Mirrors the store info from the listing
   * spec.
   */
  public record StoreInfo(
      Long storeId,
      String storeName,
      String address,
      String detailAddress,
      Double latitude,
      Double longitude) {}

  /** A single detail image slot (excludes the thumbnail). */
  public record ImageInfo(Long imageId, String finalObjectKey, int imgOrder) {}
}
