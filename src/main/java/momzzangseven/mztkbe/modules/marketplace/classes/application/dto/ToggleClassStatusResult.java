package momzzangseven.mztkbe.modules.marketplace.classes.application.dto;

/** Result of a successful class status toggle. */
public record ToggleClassStatusResult(Long classId, boolean active) {

  public static ToggleClassStatusResult of(Long classId, boolean active) {
    return new ToggleClassStatusResult(classId, active);
  }
}
