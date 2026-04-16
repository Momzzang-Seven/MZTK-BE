package momzzangseven.mztkbe.modules.marketplace.application.dto;

import java.util.List;

/** Result of the trainer's own paginated class listing. */
public record GetTrainerClassesResult(
    List<TrainerClassItem> items, int currentPage, int totalPages, long totalElements) {

  public static GetTrainerClassesResult of(
      List<TrainerClassItem> items, int currentPage, int totalPages, long totalElements) {
    return new GetTrainerClassesResult(items, currentPage, totalPages, totalElements);
  }

  /**
   * A single class item in the trainer's own class list. Includes the active flag and thumbnail
   * key.
   */
  public record TrainerClassItem(
      Long classId,
      String title,
      String category,
      int priceAmount,
      List<String> tags,
      boolean active,
      String thumbnailFinalObjectKey) {}
}
