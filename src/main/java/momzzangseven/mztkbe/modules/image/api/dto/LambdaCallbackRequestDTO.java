package momzzangseven.mztkbe.modules.image.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import momzzangseven.mztkbe.modules.image.application.dto.LambdaCallbackCommand;
import momzzangseven.mztkbe.modules.image.domain.vo.LambdaCallbackStatus;

/**
 * Request DTO for the Lambda image-processing webhook callback endpoint. Maps the JSON payload sent
 * by AWS Lambda.
 */
public record LambdaCallbackRequestDTO(
    @NotNull LambdaCallbackStatus status,
    @NotNull @NotBlank String tmpObjectKey,
    String finalObjectKey,
    String errorReason) {

  public LambdaCallbackCommand toCommand() {
    return new LambdaCallbackCommand(status, tmpObjectKey, finalObjectKey, errorReason);
  }
}
