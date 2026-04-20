package momzzangseven.mztkbe.modules.marketplace.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidCategoryException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidClassIdException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidSlotException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidTitleException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidTrainerIdException;
import momzzangseven.mztkbe.modules.marketplace.domain.vo.ClassCategory;

/**
 * Command for updating an existing marketplace class.
 *
 * <p>When a {@code ClassTimeCommand} in {@code classTimes} has a non-null {@code timeId} it is
 * treated as an update of an existing slot; otherwise it is a new slot. Slots present in the
 * database but absent from this list are candidates for deletion (soft-delete if history exists).
 */
public record UpdateClassCommand(
    Long trainerId,
    Long classId,
    String title,
    ClassCategory category,
    String description,
    int priceAmount,
    int durationMinutes,
    List<String> tags,
    List<String> features,
    String personalItems,
    List<Long> imageIds,
    List<ClassTimeCommand> classTimes) {

  /** Validates minimum required data using project-standard BusinessException subclasses. */
  public void validate() {
    if (trainerId == null || trainerId <= 0) {
      throw new MarketplaceInvalidTrainerIdException();
    }
    if (classId == null || classId <= 0) {
      throw new MarketplaceInvalidClassIdException();
    }
    if (title == null || title.isBlank()) {
      throw new MarketplaceInvalidTitleException("Title must not be blank");
    }
    if (category == null) {
      throw new MarketplaceInvalidCategoryException();
    }
    if (classTimes == null || classTimes.isEmpty()) {
      throw new MarketplaceInvalidSlotException("At least one class time must be provided");
    }
  }
}
