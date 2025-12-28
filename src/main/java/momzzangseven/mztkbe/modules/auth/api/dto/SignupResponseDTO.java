package momzzangseven.mztkbe.modules.auth.api.dto;

import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.auth.application.dto.SignupResult;

/**
 * Signup response DTO (API layer).
 */
@Getter
@Builder
public class SignupResponseDTO {
  /** Newly created user's unique identifier. */
  private Long userId;

  /**
   * Convert from Application layer DTO (SignupResult) to API layer DTO.
   *
   * <p>This method maintains layer separation: - Application Layer uses SignupResult - API Layer
   * uses SignupResponseDTO
   *
   * @param result SignupResult from application layer
   * @return SignupResponseDTO for API response
   */
  public static SignupResponseDTO from(SignupResult result) {
    return SignupResponseDTO.builder().userId(result.userId()).build();
  }
}
