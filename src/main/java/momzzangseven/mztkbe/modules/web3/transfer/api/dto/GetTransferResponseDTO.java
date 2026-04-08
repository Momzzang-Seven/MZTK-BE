package momzzangseven.mztkbe.modules.web3.transfer.api.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferSignRequestBundle;

public record GetTransferResponseDTO(
    ResourceDTO resource,
    ExecutionIntentDTO executionIntent,
    ExecutionDTO execution,
    TransferSignRequestBundle signRequest,
    TransactionDTO transaction) {

  public static GetTransferResponseDTO from(TransferExecutionIntentResult result) {
    return new GetTransferResponseDTO(
        new ResourceDTO(
            result.resourceType().name(), result.resourceId(), result.resourceStatus().name()),
        new ExecutionIntentDTO(
            result.executionIntentId(), result.executionIntentStatus().name(), result.expiresAt()),
        new ExecutionDTO(result.mode().name(), result.signCount()),
        result.signRequest(),
        result.transactionId() == null
            ? null
            : new TransactionDTO(
                result.transactionId(), result.transactionStatus().name(), result.txHash()));
  }

  public record ResourceDTO(String type, String id, String status) {}

  public record ExecutionIntentDTO(String id, String status, LocalDateTime expiresAt) {}

  public record ExecutionDTO(String mode, int signCount) {}

  public record TransactionDTO(Long id, String status, String txHash) {}
}
