package momzzangseven.mztkbe.modules.account.api.dto;

import momzzangseven.mztkbe.modules.account.application.dto.StepUpResult;

/** Response DTO for step-up authentication. */
public record StepUpResponseDTO(String accessToken, String grantType, long expiresIn) {

  public static StepUpResponseDTO from(StepUpResult result) {
    return new StepUpResponseDTO(result.accessToken(), result.grantType(), result.expiresIn());
  }
}
