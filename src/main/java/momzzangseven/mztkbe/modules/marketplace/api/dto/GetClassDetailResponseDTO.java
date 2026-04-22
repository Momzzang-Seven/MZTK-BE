package momzzangseven.mztkbe.modules.marketplace.api.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetClassDetailResult;

/** HTTP response DTO for {@code GET /marketplace/classes/{classId}}. */
public record GetClassDetailResponseDTO(
    Long classId,
    Long trainerId,
    StoreDTO store,
    String title,
    String category,
    String description,
    int priceAmount,
    String thumbnailFinalObjectKey,
    List<ImageDTO> images,
    List<String> tags,
    List<String> features,
    int durationMinutes,
    String personalItems,
    List<ClassTimeDTO> classTimes) {

  public static GetClassDetailResponseDTO from(GetClassDetailResult result) {
    StoreDTO store =
        result.store() != null
            ? new StoreDTO(
                result.store().storeId(),
                result.store().storeName(),
                result.store().address(),
                result.store().detailAddress(),
                result.store().latitude(),
                result.store().longitude())
            : null;

    List<ImageDTO> images =
        result.images() != null
            ? result.images().stream()
                .map(img -> new ImageDTO(img.imageId(), img.finalObjectKey(), img.imgOrder()))
                .toList()
            : List.of();

    List<ClassTimeDTO> classTimes =
        result.classTimes() != null
            ? result.classTimes().stream()
                .map(
                    ct ->
                        new ClassTimeDTO(
                            ct.timeId(), ct.daysOfWeek(), ct.startTime(), ct.capacity()))
                .toList()
            : List.of();

    return new GetClassDetailResponseDTO(
        result.classId(),
        result.trainerId(),
        store,
        result.title(),
        result.category(),
        result.description(),
        result.priceAmount(),
        result.thumbnailFinalObjectKey(),
        images,
        result.tags(),
        result.features(),
        result.durationMinutes(),
        result.personalItems(),
        classTimes);
  }

  /** Store info nested in the response. */
  public record StoreDTO(
      Long storeId,
      String storeName,
      String address,
      String detailAddress,
      Double latitude,
      Double longitude) {}

  /** Detail image entry (excludes thumbnail). */
  public record ImageDTO(Long imageId, String finalObjectKey, int imgOrder) {}

  /** Class time slot entry. */
  public record ClassTimeDTO(
      Long timeId, List<DayOfWeek> daysOfWeek, LocalTime startTime, int capacity) {}
}
