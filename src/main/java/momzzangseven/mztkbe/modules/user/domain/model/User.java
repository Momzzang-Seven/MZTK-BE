package momzzangseven.mztkbe.modules.user.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

@Slf4j
@Getter
@Builder
public class User {
    private Long id;
    private String email;
    /**
     * BCrypt-encoded password (only for LOCAL auth)
     * Format: $2a$10$... (60 characters)
     */
    private String password;
    private String nickname;
    private String profileImageUrl;
    /**
     * Provider-specific user ID.
     * - KAKAO: Kakao user ID (String)
     * - GOOGLE: Google user ID (String)
     * - LOCAL: null
     */
    private String provider_user_id;
    /**
     * Connected Web3 wallet address
     */
    private String walletAddress;
    private AuthProvider authProvider;
    private UserRole role;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ============================================
    // Factory Methods (Creator Pattern)
    // ============================================

    /**
     * Create a LOCAL user (email/password authentication).
     *
     * @param email User's email address
     * @param encodedPassword BCrypt-encoded password
     * @param nickname User's display name
     * @return New User instance
     * @throws IllegalArgumentException if validation fails
     */
    public static User createLocal(
            String email,
            String encodedPassword,
            String nickname
    ) {
        validateEmail(email);
        validateEncodedPassword(encodedPassword);
        validateNickname(nickname);

        LocalDateTime now = LocalDateTime.now();

        return User.builder()
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .authProvider(AuthProvider.LOCAL)
                .role(UserRole.USER)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Create a new user from Kakao OAuth data.
     */
    public static User createFromKakao(
            String kakaoId,
            String email,
            String nickname,
            String profileImageUrl
    ) {
        // Business Rule: Kakao ID is required
        if (kakaoId == null) {
            throw new IllegalArgumentException("Kakao ID is required");
        }

        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .provider_user_id(kakaoId)
                .email(email)
                .nickname(nickname)
                .profileImageUrl(profileImageUrl)
                .authProvider(AuthProvider.KAKAO)
                .role(UserRole.USER)
                .lastLoginAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Create a user from Google OAuth.
     */
    public static User createFromGoogle(
            String googleId,
            String email,
            String nickname,
            String profileImageUrl
    ) {
        if (googleId == null || googleId.isBlank()) {
            throw new IllegalArgumentException("Google ID is required");
        }

        LocalDateTime now = LocalDateTime.now();

        return User.builder()
                .email(email)
                .nickname(nickname)
                .profileImageUrl(profileImageUrl)
                .authProvider(AuthProvider.GOOGLE)
                .provider_user_id(googleId)
                .role(UserRole.USER)
                .lastLoginAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // ============================================
    // Business Logic Methods (Information Expert)
    // ============================================

    /**
     * Validate password for LOCAL authentication.
     *
     * @param rawPassword Plain text password from login request
     * @param encoder BCrypt password encoder
     * @return true if password matches, false otherwise
     * @throws IllegalStateException if called on non-LOCAL user
     */
    public boolean validatePassword(String rawPassword, PasswordEncoder encoder) {
        // Business Rule 1: Only LOCAL users can validate passwords
        if (!AuthProvider.LOCAL.equals(this.authProvider)) {
            log.error("Password validation attempted on non-LOCAL user: authProvider={}",
                    this.authProvider);
            throw new IllegalStateException(
                    "Password validation is only allowed for LOCAL users. " +
                            "This user uses: " + this.authProvider
            );
        }

        // Business Rule 2: Password field must exist
        if (this.password == null || this.password.isBlank()) {
            log.warn("User {} has no password set", this.id);
            return false;
        }

        // Business Rule 3: Input password must not be empty
        if (rawPassword == null || rawPassword.isBlank()) {
            log.debug("Empty password provided for validation");
            return false;
        }

        // Validate using BCrypt (constant-time comparison)
        try {
            boolean matches = encoder.matches(rawPassword, this.password);

            if (matches) {
                log.debug("Password validation successful for user: {}", this.id);
            } else {
                log.warn("Password validation failed for user: {}", this.id);
            }

            return matches;
        } catch (Exception e) {
            log.error("Error during password validation for user: {}", this.id, e);
            return false;
        }
    }

    /**
     * Update user profile.
     *
     * @param nickname New nickname
     * @param profileImageUrl New profile image URL
     */
    public void updateProfile(String nickname, String profileImageUrl) {
        validateNickname(nickname);
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Update password (LOCAL users only).
     *
     * @param newEncodedPassword New BCrypt-encoded password
     * @throws IllegalStateException if called on non-LOCAL user
     */
    public void updatePassword(String newEncodedPassword) {
        if (!AuthProvider.LOCAL.equals(this.authProvider)) {
            throw new IllegalStateException(
                    "Password update is only allowed for LOCAL users"
            );
        }
        validateEncodedPassword(newEncodedPassword);
        this.password = newEncodedPassword;
        this.updatedAt = LocalDateTime.now();
    }


    /**
     * Update last login timestamp.
     */
    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        log.debug("Updated last login time for user: {}", this.id);
    }

    /**
     * Check if user is admin.
     */
    public boolean isAdmin() {
        return UserRole.ADMIN.equals(this.role);
    }

    // ============================================
    // Validation Methods (Private)
    // ============================================

    /**
     * Validate email format.
     */
    private static void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        // Basic email regex
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        if (!email.matches(emailRegex)) {
            throw new IllegalArgumentException("Invalid email format: " + email);
        }
    }

    /**
     * Validate encoded password format.
     *
     * BCrypt format: $2a$10$... (60 characters total)
     */
    private static void validateEncodedPassword(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        // BCrypt format check
        if (!encodedPassword.matches("^\\$2[ayb]\\$.{56}$")) {
            throw new IllegalArgumentException(
                    "Password must be BCrypt encoded format"
            );
        }
    }

    /**
     * Validate nickname.
     */
    private static void validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("Nickname is required");
        }

        if (nickname.length() < 2 || nickname.length() > 50) {
            throw new IllegalArgumentException(
                    "Nickname must be between 2 and 50 characters"
            );
        }
    }
}