package momzzangseven.mztkbe.modules.marketplace.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Result of the trainer's own paginated class listing.
 *
 * <p>Includes the trainer's current sanction status ({@code isSuspended} / {@code suspendedUntil})
 * so the front-end can display a notice without a separate API call.
 */
public record GetTrainerClassesResult(
    boolean isSuspended,
    LocalDateTime suspendedUntil,
    List<TrainerClassItem> items,
    int currentPage,
    int totalPages,
    long totalElements) {

  /**
   * Factory method for constructing the result page.
   *
   * @param isSuspended true if the trainer is currently under an active sanction
   * @param suspendedUntil end datetime of the sanction; null when {@code isSuspended} is false
   * @param items list of class items owned by this trainer
   * @param currentPage 0-indexed current page number
   * @param totalPages total number of pages
   * @param totalElements total number of class records
   * @return the result record
   */
  public static GetTrainerClassesResult of(
      boolean isSuspended,
      LocalDateTime suspendedUntil,
      List<TrainerClassItem> items,
      int currentPage,
      int totalPages,
      long totalElements) {
    return new GetTrainerClassesResult(
        isSuspended, suspendedUntil, items, currentPage, totalPages, totalElements);
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
