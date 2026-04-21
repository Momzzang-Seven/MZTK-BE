package momzzangseven.mztkbe.modules.marketplace.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetTrainerClassesResult;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetTrainerClassesResult.TrainerClassItem;

/**
 * HTTP response DTO for {@code GET /marketplace/trainer/classes}.
 *
 * <p>Includes the trainer's current sanction status so the front-end can present a suspension
 * banner without an extra API call.
 */
public record GetTrainerClassesResponseDTO(
    boolean isSuspended,
    LocalDateTime suspendedUntil,
    List<TrainerClassItemDTO> items,
    int currentPage,
    int totalPages,
    long totalElements) {

  public static GetTrainerClassesResponseDTO from(GetTrainerClassesResult result) {
    List<TrainerClassItemDTO> items =
        result.items().stream().map(TrainerClassItemDTO::from).toList();
    return new GetTrainerClassesResponseDTO(
        result.isSuspended(),
        result.suspendedUntil(),
        items,
        result.currentPage(),
        result.totalPages(),
        result.totalElements());
  }

  /** Single class item in the trainer's own class list. */
  public record TrainerClassItemDTO(
      Long classId,
      String title,
      String category,
      int priceAmount,
      List<String> tags,
      boolean active,
      String thumbnailFinalObjectKey) {

    public static TrainerClassItemDTO from(TrainerClassItem item) {
      return new TrainerClassItemDTO(
          item.classId(),
          item.title(),
          item.category(),
          item.priceAmount(),
          item.tags(),
          item.active(),
          item.thumbnailFinalObjectKey());
    }
  }
}
