package momzzangseven.mztkbe.modules.web3.transfer.api.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;

public record CreateTransferResponseDTO(
    ResourceDTO resource,
    ExecutionIntentDTO executionIntent,
    ExecutionDTO execution,
    SignRequestBundle signRequest,
    boolean existing) {

  public static CreateTransferResponseDTO from(CreateExecutionIntentResult result) {
    return new CreateTransferResponseDTO(
        new ResourceDTO(result.resourceType().name(), result.resourceId(), result.resourceStatus()),
        new ExecutionIntentDTO(
            result.executionIntentId(), result.executionIntentStatus().name(), result.expiresAt()),
        new ExecutionDTO(result.mode().name(), result.signCount()),
        result.signRequest(),
        result.existing());
  }

  public record ResourceDTO(String type, String id, String status) {}

  public record ExecutionIntentDTO(String id, String status, LocalDateTime expiresAt) {}

  public record ExecutionDTO(String mode, int signCount) {}
}
