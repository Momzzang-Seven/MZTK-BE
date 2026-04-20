package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;

public record ExecuteInternalExecutionIntentCommand(List<ExecutionActionType> actionTypes) {

  public ExecuteInternalExecutionIntentCommand {
    if (actionTypes == null || actionTypes.isEmpty()) {
      throw new Web3InvalidInputException("actionTypes is required");
    }
    if (actionTypes.stream().anyMatch(java.util.Objects::isNull)) {
      throw new Web3InvalidInputException("actionTypes contains null");
    }
  }
}
