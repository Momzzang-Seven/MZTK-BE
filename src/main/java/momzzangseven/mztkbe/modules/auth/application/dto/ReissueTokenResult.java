package momzzangseven.mztkbe.modules.auth.application.dto;

import lombok.Builder;

/**
 * Result of token reissue operation.
 *
 * <p>Data Transfer Object: - Transfers data from Application layer to API layer - Immutable
 * (via @Builder) - Contains only necessary data for response
 *
 * <p>Pattern: Result Pattern - Encapsulates operation result - Provides type-safe data transfer
 */
@Builder
public record ReissueTokenResult(
    String accessToken,
    String refreshToken,
    String grantType,
    Long accessTokenExpiresIn,
    Long refreshTokenExpiresIn) {
  /**
   * Create result with default grant type.
   *
   * @param accessToken New access token
   * @param refreshToken New refresh token
   * @param accessTokenExpiresIn Access Token expiration in milliseconds
   * @param refreshTokenExpiresIn Refresh Token expiration in milliseconds
   * @return ReissueTokenResult
   */
  public static ReissueTokenResult of(
      String accessToken,
      String refreshToken,
      long accessTokenExpiresIn,
      long refreshTokenExpiresIn) {

    return ReissueTokenResult.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .grantType("Bearer")
        .accessTokenExpiresIn(accessTokenExpiresIn)
        .refreshTokenExpiresIn(refreshTokenExpiresIn)
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
    if (accessTokenExpiresIn <= 0 || refreshTokenExpiresIn <= 0) {
      throw new IllegalStateException("ExpiresIn must be positive");
    }
  }
}
