package momzzangseven.mztkbe.modules.web3.execution.application.dto;

public record RunInternalExecutionBatchResult(
    int executedCount, int pendingCount, int signedCount, int quarantinedCount, int failedCount) {

  public boolean isEmpty() {
    return executedCount <= 0 && quarantinedCount <= 0 && failedCount <= 0;
  }
}
