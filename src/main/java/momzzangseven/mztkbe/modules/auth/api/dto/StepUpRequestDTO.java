package momzzangseven.mztkbe.modules.auth.api.dto;

/**
 * Request DTO for step-up authentication (re-authentication for sensitive operations).
 *
 * <p>Unlike login, step-up is performed with an already authenticated user (access token present),
 * so the server already knows the userId and provider. The client only provides the additional
 * credential needed for re-authentication:
 *
 * <ul>
 *   <li>LOCAL: {@code password}
 *   <li>SOCIAL: {@code authorizationCode}
 * </ul>
 *
 * <p>For safety and clarity, the server rejects requests that provide the wrong credential for the
 * authenticated user's provider (e.g., sending {@code authorizationCode} for a LOCAL user).
 */
public record StepUpRequestDTO(String password, String authorizationCode) {}
