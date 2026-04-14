package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;

public record ExecuteExecutionIntentResult(
    String executionIntentId,
    ExecutionIntentStatus executionIntentStatus,
    Long transactionId,
    ExecutionTransactionStatus transactionStatus,
    String txHash) {

  public ExecuteExecutionIntentResult {
    if (executionIntentId == null || executionIntentId.isBlank()) {
      throw new Web3InvalidInputException("executionIntentId is required");
    }
    if (executionIntentStatus == null) {
      throw new Web3InvalidInputException("executionIntentStatus is required");
    }
  }
}
