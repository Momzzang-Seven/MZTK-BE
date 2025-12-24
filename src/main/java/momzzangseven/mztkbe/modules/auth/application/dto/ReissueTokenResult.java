package momzzangseven.mztkbe.modules.auth.application.dto;

import lombok.Builder;

/**
 * Result of token reissue operation.
 *
 * <p>Data Transfer Object:
 * - Transfers data from Application layer to API layer
 * - Immutable (via @Builder)
 * - Contains only necessary data for response
 *
 * <p>Pattern: Result Pattern
 * - Encapsulates operation result
 * - Provides type-safe data transfer
 */
@Builder
public record ReissueTokenResult(String accessToken,
                                 String refreshToken,
                                 String grantType,
                                 long expiresIn) {
    /**
     * Create result with default grant type.
     *
     * @param accessToken New access token
     * @param refreshToken New refresh token
     * @param expiresIn Token expiration in seconds
     * @return ReissueTokenResult
     */
    public static ReissueTokenResult of(
            String accessToken,
            String refreshToken,
            long expiresIn) {

        return ReissueTokenResult.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .grantType("Bearer")
                .expiresIn(expiresIn)
                .build();
    }

    /**
     * Validate result.
     *
     * @throws IllegalStateException if result is invalid
     */
    public void validate() {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Access token cannot be empty");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalStateException("Refresh token cannot be empty");
        }
        if (expiresIn <= 0) {
            throw new IllegalStateException("ExpiresIn must be positive");
        }
    }
}
