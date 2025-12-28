package momzzangseven.mztkbe.modules.auth.api.dto.token;

import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.auth.application.dto.ReissueTokenResult;

/**
 * Response DTO for token reissue (API Layer).
 *
 * <p>Security Design: - Access Token: Included in response body (client stores in memory) - Refresh
 * Token: Sent via HttpOnly cookie (not in response body)
 *
 * <p>Maps from ReissueTokenResult (Application Layer).
 */
@Getter
@Builder
public class ReissueTokenResponseDTO {

  /**
   * New access token (JWT).
   *
   * <p>Client should store in memory (NOT localStorage).
   *
   * <p>Short-lived
   */
  private String accessToken;

  /** Token type (always "Bearer"). */
  private String grantType;

  /** Access token expiration time in milliseconds. */
  private Long expiresIn;

  /**
   * Convert from Application Layer Result to API Layer DTO.
   *
   * @param result ReissueTokenResult from application layer
   * @return ReissueTokenResponseDTO for API response
   */
  public static ReissueTokenResponseDTO from(ReissueTokenResult result) {
    return ReissueTokenResponseDTO.builder()
        .accessToken(result.accessToken())
        .grantType(result.grantType())
        .expiresIn(result.accessTokenExpiresIn())
        .build();
  }
}
