package momzzangseven.mztkbe.modules.auth.application.dto;

/**
 * Command for step-up authentication.
 *
 * <p>Step-up is performed for an already authenticated user (access token present), so {@code
 * userId} is required and comes from the security context.
 *
 * <p>Only one credential is expected depending on the user's provider:
 *
 * <ul>
 *   <li>LOCAL: {@code password}
 *   <li>KAKAO/GOOGLE: {@code authorizationCode}
 * </ul>
 *
 * <p>Provider-specific credential validation is intentionally performed in {@code StepUpService}
 * after loading the user, because the provider is derived from the persisted user record.
 */
public record StepUpCommand(Long userId, String password, String authorizationCode) {

  /**
   * Create command.
   *
   * <p>Validation is executed in the application service.
   */
  public static StepUpCommand of(Long userId, String password, String authorizationCode) {
    return new StepUpCommand(userId, password, authorizationCode);
  }

  /** Validate command parameters that do not depend on provider. */
  public void validate() {
    if (userId == null) {
      throw new IllegalArgumentException("userId is required");
    }
  }
}
