package momzzangseven.mztkbe.modules.auth.api.dto;

import momzzangseven.mztkbe.modules.auth.application.dto.StepUpResult;

/** Response DTO for step-up authentication. */
public record StepUpResponseDTO(String accessToken, String grantType, long expiresIn) {

  public static StepUpResponseDTO from(StepUpResult result) {
    return new StepUpResponseDTO(result.accessToken(), result.grantType(), result.expiresIn());
  }
}
