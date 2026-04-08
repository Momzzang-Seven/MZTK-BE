package momzzangseven.mztkbe.modules.web3.transfer.api.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferSignRequestBundle;

/** API response DTO returned by transfer create endpoint. */
public record CreateTransferResponseDTO(
    ResourceDTO resource,
    ExecutionIntentDTO executionIntent,
    ExecutionDTO execution,
    TransferSignRequestBundle signRequest,
    boolean existing) {

  /** Maps transfer execution create result into transfer API response contract. */
  public static CreateTransferResponseDTO from(TransferExecutionIntentResult result) {
    return new CreateTransferResponseDTO(
        new ResourceDTO(result.resourceType(), result.resourceId(), result.resourceStatus()),
        new ExecutionIntentDTO(
            result.executionIntentId(), result.executionIntentStatus(), result.expiresAt()),
        new ExecutionDTO(result.mode(), result.signCount()),
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
