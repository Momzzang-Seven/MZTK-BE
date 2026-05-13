package momzzangseven.mztkbe.modules.web3.execution.api.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;

/** API response DTO for execution intent read endpoint. */
public record GetExecutionIntentResponseDTO(
    ResourceDTO resource,
    ExecutionIntentDTO executionIntent,
    ExecutionDTO execution,
    SignRequestBundle signRequest,
    String signRequestUnavailableReason,
    TransactionDTO transaction) {

  /** Maps application read result to stable API response shape. */
  public static GetExecutionIntentResponseDTO from(GetExecutionIntentResult result) {
    return new GetExecutionIntentResponseDTO(
        new ResourceDTO(
            result.resourceType().name(), result.resourceId(), result.resourceStatus().name()),
        new ExecutionIntentDTO(
            result.executionIntentId(),
            result.executionIntentStatus().name(),
            result.expiresAt(),
            result.expiresAtEpochSeconds()),
        new ExecutionDTO(result.mode().name(), result.signCount()),
        result.signRequest(),
        result.signRequestUnavailableReason() == null
            ? null
            : result.signRequestUnavailableReason().name(),
        result.transactionId() == null
            ? null
            : new TransactionDTO(
                result.transactionId(), result.transactionStatus().name(), result.txHash()));
  }

  /** Resource section containing domain resource identifier and derived status. */
  public record ResourceDTO(String type, String id, String status) {}

  /** Execution intent section containing lifecycle state and expiration timestamp. */
  public record ExecutionIntentDTO(
      String id, String status, LocalDateTime expiresAt, long expiresAtEpochSeconds) {}

  /** Execution metadata section containing selected mode and required sign count. */
  public record ExecutionDTO(String mode, int signCount) {}

  /** Linked transaction summary section when execution has submitted transaction. */
  public record TransactionDTO(Long id, String status, String txHash) {}
}
