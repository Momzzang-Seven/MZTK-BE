package momzzangseven.mztkbe.modules.auth.application.dto;

import momzzangseven.mztkbe.modules.auth.api.dto.LoginRequestDTO;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;

/**
 * Login command passed to LoginUseCase.
 *
 * Converted from API layer's LoginRequest.
 */
public record LoginCommand(
        AuthProvider provider,
        String email,
        String password,
        String authorizationCode,
        String redirectUri
) {

    /**
     * Create LoginCommand from API request.
     *
     * @param request LoginRequest from controller
     * @return LoginCommand for use case
     */
    public static LoginCommand from(LoginRequestDTO request) {
        return new LoginCommand(
                request.getProvider(),
                request.getEmail(),
                request.getPassword(),
                request.getAuthorizationCode(),
                request.getRedirectUri()
        );
    }

    /**
     * Validate command based on provider.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public void validate() {
        if (provider == null) {
            throw new IllegalArgumentException("Provider is required");
        }

        switch (provider) {
            case LOCAL:
                if (email == null || email.isBlank()) {
                    throw new IllegalArgumentException("Email is required for LOCAL login");
                }
                if (password == null || password.isBlank()) {
                    throw new IllegalArgumentException("Password is required for LOCAL login");
                }
                break;

            case KAKAO:
            case GOOGLE:
                if (authorizationCode == null || authorizationCode.isBlank()) {
                    throw new IllegalArgumentException(
                            "Authorization code is required for " + provider + " login"
                    );
                }
                break;

            default:
                throw new IllegalArgumentException("Unsupported provider: " + provider);
        }
    }
}