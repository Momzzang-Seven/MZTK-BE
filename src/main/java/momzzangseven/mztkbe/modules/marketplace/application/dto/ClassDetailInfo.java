package momzzangseven.mztkbe.modules.marketplace.application.dto;

/**
 * DTO used by the persistence layer to carry the detailed class projection, including joined store
 * data and a flat slot list.
 *
 * <p>This is an internal application-layer DTO filled by the persistence adapter and consumed by
 * {@link momzzangseven.mztkbe.modules.marketplace.application.service.GetClassDetailService}.
 *
 * <p><b>Note on {@code classTimes}</b>: the persistence adapter always populates this field with
 * {@code List.of()} (empty). Active class time slots are loaded separately by {@link
 * momzzangseven.mztkbe.modules.marketplace.application.service.GetClassDetailService} via {@link
 * momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassSlotPort} and merged into
 * the final {@link momzzangseven.mztkbe.modules.marketplace.application.dto.GetClassDetailResult}.
 * This two-step design avoids a JOIN on {@code class_slots} in the same query that already JOINs
 * {@code trainer_stores}.
 */
public record ClassDetailInfo(
    Long classId,
    Long trainerId,
    Long storeId,
    String storeName,
    String storeAddress,
    String storeDetailAddress,
    Double storeLatitude,
    Double storeLongitude,
    String title,
    String category,
    String description,
    int priceAmount,
    int durationMinutes,
    java.util.List<String> tags,
    java.util.List<String> features,
    String personalItems,
    java.util.List<ClassSlotInfo> classTimes) {}
