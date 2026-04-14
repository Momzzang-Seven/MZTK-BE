package momzzangseven.mztkbe.modules.account.application.dto;

/**
 * Result of step-up authentication.
 *
 * @param accessToken Step-up access token (JWT)
 * @param grantType Token type (usually "Bearer")
 * @param expiresIn Token TTL (in milliseconds)
 */
public record StepUpResult(String accessToken, String grantType, long expiresIn) {

  public static StepUpResult of(String accessToken, long expiresIn) {
    return new StepUpResult(accessToken, "Bearer", expiresIn);
  }
}
