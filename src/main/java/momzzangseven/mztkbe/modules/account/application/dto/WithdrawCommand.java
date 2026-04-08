package momzzangseven.mztkbe.modules.account.application.dto;

/** Command for withdrawing (soft-deleting) the currently authenticated user's account. */
public record WithdrawCommand(Long userId) {

  public static WithdrawCommand of(Long userId) {
    return new WithdrawCommand(userId);
  }

  /** Validate required fields. */
  public void validate() {
    if (userId == null) {
      throw new IllegalArgumentException("userId is required");
    }
  }
}
