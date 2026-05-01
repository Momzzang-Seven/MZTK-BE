package momzzangseven.mztkbe.modules.web3.execution.domain.event;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;

/** Domain event published after an execution intent enters a terminal state. */
public record ExecutionIntentTerminatedEvent(
    String executionIntentId, ExecutionIntentStatus terminalStatus, String failureReason) {

  public ExecutionIntentTerminatedEvent {
    if (executionIntentId == null || executionIntentId.isBlank()) {
      throw new Web3InvalidInputException("executionIntentId is required");
    }
    if (terminalStatus == null) {
      throw new Web3InvalidInputException("terminalStatus is required");
    }
    if (!terminalStatus.isTerminal()) {
      throw new Web3InvalidInputException("terminalStatus must be terminal");
    }
  }
}
