package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;

public record ExecuteInternalExecutionIntentResult(
    boolean executed,
    boolean quarantined,
    String executionIntentId,
    ExecutionIntentStatus executionIntentStatus,
    Long transactionId,
    ExecutionTransactionStatus transactionStatus,
    String txHash) {

  public static ExecuteInternalExecutionIntentResult notFound() {
    return new ExecuteInternalExecutionIntentResult(false, false, null, null, null, null, null);
  }

  public static ExecuteInternalExecutionIntentResult quarantined(
      String executionIntentId, ExecutionIntentStatus executionIntentStatus) {
    return new ExecuteInternalExecutionIntentResult(
        true, true, executionIntentId, executionIntentStatus, null, null, null);
  }
}
