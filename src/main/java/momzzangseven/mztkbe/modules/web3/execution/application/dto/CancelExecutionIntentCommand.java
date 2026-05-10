package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;

public record CancelExecutionIntentCommand(
    String executionIntentId, String errorCode, String errorReason) {

  public void validate() {
    if (executionIntentId == null || executionIntentId.isBlank()) {
      throw new IllegalArgumentException("executionIntentId is required");
    }
  }

  public String resolvedErrorCode() {
    return errorCode == null || errorCode.isBlank()
        ? ExecutionIntentStatus.CANCELED.name()
        : errorCode;
  }

  public String resolvedErrorReason() {
    return errorReason == null || errorReason.isBlank() ? "execution intent canceled" : errorReason;
  }
}
