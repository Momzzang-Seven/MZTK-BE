package momzzangseven.mztkbe.modules.marketplace.application.dto;

/** Result of a successful class registration. */
public record RegisterClassResult(Long classId) {

  public static RegisterClassResult of(Long classId) {
    return new RegisterClassResult(classId);
  }
}
