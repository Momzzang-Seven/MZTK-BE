package momzzangseven.mztkbe.modules.user.application.dto;

/** Command for withdrawing (soft-deleting) the currently authenticated user. */
public record WithdrawUserCommand(Long userId) {

  public static WithdrawUserCommand of(Long userId) {
    return new WithdrawUserCommand(userId);
  }

  public void validate() {
    if (userId == null) {
      throw new IllegalArgumentException("userId is required");
    }
  }
}
