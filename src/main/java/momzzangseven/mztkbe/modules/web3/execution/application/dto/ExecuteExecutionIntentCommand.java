package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record ExecuteExecutionIntentCommand(
    Long requesterUserId,
    String executionIntentId,
    String authorizationSignature,
    String submitSignature,
    String signedRawTransaction) {

  public ExecuteExecutionIntentCommand {
    if (requesterUserId == null || requesterUserId <= 0) {
      throw new Web3InvalidInputException("requesterUserId must be positive");
    }
    if (executionIntentId == null || executionIntentId.isBlank()) {
      throw new Web3InvalidInputException("executionIntentId is required");
    }
  }
}
