package momzzangseven.mztkbe.modules.account.application.dto;

/**
 * Command object for signup use case.
 *
 * <p>Application Layer DTO that encapsulates signup request data. This separates API layer concerns
 * from business logic.
 */
public record SignupCommand(
    String email,
    String password, // Plain text password (will be encoded in service)
    String nickname,
    String role) {

  /**
   * Validate command data.
   *
   * <p>Additional business validations beyond Jakarta Validation.
   */
  public void validate() {
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Email is required");
    }
    if (password == null || password.isBlank()) {
      throw new IllegalArgumentException("Password is required");
    }
    if (nickname == null || nickname.isBlank()) {
      throw new IllegalArgumentException("Nickname is required");
    }
  }
}
