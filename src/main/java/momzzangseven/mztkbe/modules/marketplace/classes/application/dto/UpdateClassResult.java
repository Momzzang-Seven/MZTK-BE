package momzzangseven.mztkbe.modules.marketplace.classes.application.dto;

/** Result of a successful class update. */
public record UpdateClassResult(Long classId) {

  public static UpdateClassResult of(Long classId) {
    return new UpdateClassResult(classId);
  }
}
