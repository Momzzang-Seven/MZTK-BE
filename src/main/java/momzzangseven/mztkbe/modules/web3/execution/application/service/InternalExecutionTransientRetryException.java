package momzzangseven.mztkbe.modules.web3.execution.application.service;

import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;

public class InternalExecutionTransientRetryException extends RuntimeException {

  private final String executionIntentId;
  private final ExecutionIntentStatus executionIntentStatus;

  public InternalExecutionTransientRetryException(
      String executionIntentId, ExecutionIntentStatus executionIntentStatus, Throwable cause) {
    super(cause);
    this.executionIntentId = executionIntentId;
    this.executionIntentStatus = executionIntentStatus;
  }

  public String executionIntentId() {
    return executionIntentId;
  }

  public ExecutionIntentStatus executionIntentStatus() {
    return executionIntentStatus;
  }
}
