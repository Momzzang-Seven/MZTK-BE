package momzzangseven.mztkbe.modules.auth.application.dto;

import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;

/**
 * Authentication context passed to AuthenticationStrategy.
 *
 * This DTO contains all necessary information for authentication,
 * regardless of the authentication method (LOCAL, KAKAO, GOOGLE).
 *
 * Different strategies use different fields:
 * - LOCAL: email, password
 * - KAKAO: authorizationCode, redirectUri
 * - GOOGLE: authorizationCode, redirectUri
 */
public record AuthenticationContext(

        /**
         * Authentication provider (LOCAL, KAKAO, GOOGLE)
         */
        AuthProvider provider,

        /**
         * Email address (for LOCAL authentication)
         */
        String email,

        /**
         * Password (for LOCAL authentication)
         */
        String password,

        /**
         * Authorization code from OAuth callback (for KAKAO, GOOGLE)
         */
        String authorizationCode,

        /**
         * Redirect URI used in OAuth flow (optional, for validation)
         */
        String redirectUri
) {

    /**
     * Create AuthenticationContext from LoginCommand.
     *
     * @param command LoginCommand from use case
     * @return AuthenticationContext for strategy
     */
    public static AuthenticationContext from(LoginCommand command) {
        return new AuthenticationContext(
                command.provider(),
                command.email(),
                command.password(),
                command.authorizationCode(),
                command.redirectUri()
        );
    }

    /**
     * Validate context for LOCAL authentication.
     *
     * @return true if email and password are present
     */
    public boolean isValidForLocal() {
        return email != null && !email.isBlank()
                && password != null && !password.isBlank();
    }

    /**
     * Validate context for social authentication (KAKAO, GOOGLE).
     *
     * @return true if authorizationCode is present
     */
    public boolean isValidForSocial() {
        return authorizationCode != null && !authorizationCode.isBlank();
    }
}
