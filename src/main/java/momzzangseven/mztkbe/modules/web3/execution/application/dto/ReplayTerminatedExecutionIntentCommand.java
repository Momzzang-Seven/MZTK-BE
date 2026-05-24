package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record ReplayTerminatedExecutionIntentCommand(
    String executionIntentId, String expectedActionType) {

  public void validate() {
    if (executionIntentId == null || executionIntentId.isBlank()) {
      throw new Web3InvalidInputException("executionIntentId is required");
    }
    if (expectedActionType == null || expectedActionType.isBlank()) {
      throw new Web3InvalidInputException("expectedActionType is required");
    }
  }
}
