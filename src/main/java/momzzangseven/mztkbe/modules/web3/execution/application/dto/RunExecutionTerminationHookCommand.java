package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.modules.web3.execution.domain.event.ExecutionIntentTerminatedEvent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;

public record RunExecutionTerminationHookCommand(
    String executionIntentId, ExecutionIntentStatus terminalStatus, String failureReason) {

  public static RunExecutionTerminationHookCommand from(ExecutionIntentTerminatedEvent event) {
    return new RunExecutionTerminationHookCommand(
        event.executionIntentId(), event.terminalStatus(), event.failureReason());
  }
}
