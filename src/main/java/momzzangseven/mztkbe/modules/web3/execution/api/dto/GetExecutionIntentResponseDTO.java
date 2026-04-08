package momzzangseven.mztkbe.modules.web3.execution.api.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

public record GetExecutionIntentResponseDTO(
    ResourceDTO resource,
    ExecutionIntentDTO executionIntent,
    ExecutionDTO execution,
    SignRequestBundle signRequest,
    TransactionDTO transaction) {

  public static GetExecutionIntentResponseDTO from(GetExecutionIntentResult result) {
    return new GetExecutionIntentResponseDTO(
        new ResourceDTO(
            result.resourceType().name(), result.resourceId(), result.resourceStatus().name()),
        new ExecutionIntentDTO(
            result.executionIntentId(), result.executionIntentStatus().name(), result.expiresAt()),
        new ExecutionDTO(result.mode().name(), result.signCount()),
        result.signRequest(),
        result.transactionId() == null
            ? null
            : new TransactionDTO(
                result.transactionId(), result.transactionStatus(), result.txHash()));
  }

  public record ResourceDTO(String type, String id, String status) {}

  public record ExecutionIntentDTO(String id, String status, LocalDateTime expiresAt) {}

  public record ExecutionDTO(String mode, int signCount) {}

  public record TransactionDTO(Long id, Web3TxStatus status, String txHash) {}
}
