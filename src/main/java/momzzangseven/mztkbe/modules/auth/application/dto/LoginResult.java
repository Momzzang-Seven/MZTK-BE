package momzzangseven.mztkbe.modules.auth.application.dto;

import lombok.Builder;
import momzzangseven.mztkbe.modules.user.domain.model.User;

/**
 * Result of login use case execution.
 *
 * Contains all information needed for API response.
 */
@Builder
public record LoginResult(

        /**
         * JWT access token
         */
        String accessToken,

        /**
         * JWT refresh token
         */
        String refreshToken,

        /**
         * Token type (usually "Bearer")
         */
        String grantType,

        /**
         * Access token expiration time in seconds
         */
        Integer expiresIn,

        /**
         * Whether this is a newly registered user
         */
        Boolean isNewUser,

        /**
         * Authenticated user information
         */
        User user
) {
}
