package momzzangseven.mztkbe.modules.marketplace.application.dto;

import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidSlotException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidTrainerIdException;

/** Command for toggling the active/inactive status of a class. */
public record ToggleClassStatusCommand(Long trainerId, Long classId) {

  /** Validates that the command carries the minimum required data. */
  public void validate() {
    if (trainerId == null || trainerId <= 0) {
      throw new MarketplaceInvalidTrainerIdException();
    }
    if (classId == null || classId <= 0) {
      throw new MarketplaceInvalidSlotException("Class ID must be positive");
    }
  }
}
