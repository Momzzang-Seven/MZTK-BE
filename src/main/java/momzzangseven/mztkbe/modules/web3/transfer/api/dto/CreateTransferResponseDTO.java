package momzzangseven.mztkbe.modules.web3.transfer.api.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;

/** API response DTO returned by transfer create endpoint. */
public record CreateTransferResponseDTO(
    ResourceDTO resource,
    ExecutionIntentDTO executionIntent,
    ExecutionDTO execution,
    SignRequestBundle signRequest,
    boolean existing) {

  /** Maps shared execution create result into transfer API response contract. */
  public static CreateTransferResponseDTO from(CreateExecutionIntentResult result) {
    return new CreateTransferResponseDTO(
        new ResourceDTO(
            result.resourceType().name(), result.resourceId(), result.resourceStatus().name()),
        new ExecutionIntentDTO(
            result.executionIntentId(), result.executionIntentStatus().name(), result.expiresAt()),
        new ExecutionDTO(result.mode().name(), result.signCount()),
        result.signRequest(),
        result.existing());
  }

  /** Resource section for created transfer execution target. */
  public record ResourceDTO(String type, String id, String status) {}

  /** Execution intent section for transfer create response. */
  public record ExecutionIntentDTO(String id, String status, LocalDateTime expiresAt) {}

  /** Execution metadata section with selected mode and sign count. */
  public record ExecutionDTO(String mode, int signCount) {}
}
