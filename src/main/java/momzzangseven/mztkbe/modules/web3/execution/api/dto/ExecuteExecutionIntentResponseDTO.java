package momzzangseven.mztkbe.modules.web3.execution.api.dto;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

public record ExecuteExecutionIntentResponseDTO(
    ExecutionIntentDTO executionIntent, TransactionDTO transaction) {

  public static ExecuteExecutionIntentResponseDTO from(ExecuteExecutionIntentResult result) {
    return new ExecuteExecutionIntentResponseDTO(
        new ExecutionIntentDTO(result.executionIntentId(), result.executionIntentStatus().name()),
        result.transactionId() == null
            ? null
            : new TransactionDTO(
                result.transactionId(), result.transactionStatus(), result.txHash()));
  }

  public record ExecutionIntentDTO(String id, String status) {}

  public record TransactionDTO(Long id, Web3TxStatus status, String txHash) {}
}
