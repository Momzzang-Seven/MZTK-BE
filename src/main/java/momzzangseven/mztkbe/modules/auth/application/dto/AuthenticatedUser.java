package momzzangseven.mztkbe.modules.auth.application.dto;

import momzzangseven.mztkbe.modules.user.domain.model.User;

/**
 * Result of authentication process.
 *
 * Returned by AuthenticationStrategy after successful authentication.
 * Contains authenticated user and additional context.
 */
public record AuthenticatedUser(

        /**
         * Authenticated user
         */
        User user,

        /**
         * Whether this is a newly created user (auto-registered via social login)
         */
        boolean isNewUser
) {

    /**
     * Create AuthenticatedUser for existing user.
     *
     * @param user Existing user
     * @return AuthenticatedUser with isNewUser=false
     */
    public static AuthenticatedUser existing(User user) {
        return new AuthenticatedUser(user, false);
    }

    /**
     * Create AuthenticatedUser for new user.
     *
     * @param user Newly created user
     * @return AuthenticatedUser with isNewUser=true
     */
    public static AuthenticatedUser newUser(User user) {
        return new AuthenticatedUser(user, true);
    }
}