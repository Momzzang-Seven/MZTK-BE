package momzzangseven.mztkbe.modules.marketplace.application.dto;

/** Result of a successful class update. */
public record UpdateClassResult(Long classId) {

  public static UpdateClassResult of(Long classId) {
    return new UpdateClassResult(classId);
  }
}
