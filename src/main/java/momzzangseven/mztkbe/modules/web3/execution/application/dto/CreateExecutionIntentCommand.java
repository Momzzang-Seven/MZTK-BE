package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record CreateExecutionIntentCommand(ExecutionDraft draft) {

  public CreateExecutionIntentCommand {
    if (draft == null) {
      throw new Web3InvalidInputException("draft is required");
    }
  }
}
