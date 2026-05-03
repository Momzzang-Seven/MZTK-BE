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

  /**
   * Neutral outcome for a transient KMS failure: the reserved nonce was released, the intent stays
   * in {@link ExecutionIntentStatus#AWAITING_SIGNATURE}, and the next scheduler tick re-claims it.
   * Reported with {@code executed=true, quarantined=false} so {@link
   * momzzangseven.mztkbe.modules.web3.execution.application.service.RunInternalExecutionBatchService}
   * does not increment {@code failedCount} (which would break the batch loop) and does not count
   * toward {@code quarantinedCount}.
   */
  public static ExecuteInternalExecutionIntentResult transientRetry(
      String executionIntentId, ExecutionIntentStatus executionIntentStatus) {
    return new ExecuteInternalExecutionIntentResult(
        true, false, executionIntentId, executionIntentStatus, null, null, null);
  }
}
