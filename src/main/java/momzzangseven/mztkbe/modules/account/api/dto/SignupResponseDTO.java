package momzzangseven.mztkbe.modules.account.api.dto;

import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.account.application.dto.SignupResult;

/** Signup response DTO (API layer). */
@Getter
@Builder
public class SignupResponseDTO {
  /** Newly created user's unique identifier. */
  private Long userId;

  /** User's email address. */
  private String email;

  /** User's display nickname. */
  private String nickname;

  /** Assigned role (e.g. "USER", "TRAINER"). */
  private String role;

  /**
   * Convert from Application layer DTO (SignupResult) to API layer DTO.
   *
   * @param result SignupResult from application layer
   * @return SignupResponseDTO for API response
   */
  public static SignupResponseDTO from(SignupResult result) {
    return SignupResponseDTO.builder()
        .userId(result.userId())
        .email(result.email())
        .nickname(result.nickname())
        .role(result.role())
        .build();
  }
}
