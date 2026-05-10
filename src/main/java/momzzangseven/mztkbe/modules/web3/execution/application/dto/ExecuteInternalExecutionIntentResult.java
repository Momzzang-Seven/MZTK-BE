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
   * Outcome for a transient KMS failure: the reserved nonce was released, the intent stays in
   * {@link ExecutionIntentStatus#AWAITING_SIGNATURE}, and the next cron tick re-claims it.
   *
   * <p>Reported with {@code executed=false} so {@link
   * momzzangseven.mztkbe.modules.web3.execution.application.service.RunInternalExecutionBatchService}
   * exits the batch loop immediately. Without this break, a single transient KMS error on the
   * oldest AWAITING intent would re-fire {@code batchSize} times in one tick (the FOR UPDATE SKIP
   * LOCKED row releases on the inner REQUIRES_NEW commit, and {@code claimNext...} re-orders by
   * {@code created_at}, so the same row is re-claimed). Trade-off: when other AWAITING intents
   * exist, they are delayed by one cron tick rather than processed in the same tick — acceptable
   * because transient KMS errors are rare and the cron cadence is the existing pacing primitive.
   * All terminal/quarantine paths still leave {@code executed=true}; only this explicitly retryable
   * transient case early-exits the loop.
   */
  public static ExecuteInternalExecutionIntentResult transientRetry(
      String executionIntentId, ExecutionIntentStatus executionIntentStatus) {
    return new ExecuteInternalExecutionIntentResult(
        false, false, executionIntentId, executionIntentStatus, null, null, null);
  }

  /**
   * Outcome when sponsor-wallet preflight (load + active + structural + KMS DescribeKey verify)
   * fails before any intent is claimed. Reported with {@code executed=false} so the batch loop
   * exits immediately — there is no point hammering KMS for the next intent on this tick when the
   * sponsor wallet itself is unavailable.
   *
   * <p>No {@code ExecutionIntentTerminatedEvent} is published: no intent has been claimed, so there
   * is nothing to cascade to consumer modules (no QnA escrow refund cascade fires).
   */
  public static ExecuteInternalExecutionIntentResult preflightSkipped() {
    return new ExecuteInternalExecutionIntentResult(false, false, null, null, null, null, null);
  }
}
