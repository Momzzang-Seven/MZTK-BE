package momzzangseven.mztkbe.modules.verification.application.dto;

/** Input command for a synchronous workout verification submit call. */
public record SubmitWorkoutVerificationCommand(Long userId, String tmpObjectKey) {

  public void validate() {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (tmpObjectKey == null || tmpObjectKey.isBlank()) {
      throw new IllegalArgumentException("tmpObjectKey must not be blank");
    }
  }
}
