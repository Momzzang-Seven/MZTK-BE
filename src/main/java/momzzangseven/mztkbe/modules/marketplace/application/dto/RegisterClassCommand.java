package momzzangseven.mztkbe.modules.marketplace.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidCategoryException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidSlotException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidTitleException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidTrainerIdException;
import momzzangseven.mztkbe.modules.marketplace.domain.vo.ClassCategory;

/**
 * Command for registering a new marketplace class.
 *
 * <p>Carries all user-supplied fields plus the authenticated trainer's ID. {@link #validate()}
 * performs basic sanity checks using project-standard {@code BusinessException} subclasses; deep
 * domain invariants are enforced by the domain model factory.
 */
public record RegisterClassCommand(
    Long trainerId,
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

  /** Validates that the command carries the minimum required data. */
  public void validate() {
    if (trainerId == null || trainerId <= 0) {
      throw new MarketplaceInvalidTrainerIdException();
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
