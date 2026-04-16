package momzzangseven.mztkbe.modules.marketplace.application.dto;

/** Command for toggling the active/inactive status of a class. */
public record ToggleClassStatusCommand(Long trainerId, Long classId) {

  public void validate() {
    if (trainerId == null || trainerId <= 0) {
      throw new IllegalArgumentException("Trainer ID must be positive");
    }
    if (classId == null || classId <= 0) {
      throw new IllegalArgumentException("Class ID must be positive");
    }
  }
}
