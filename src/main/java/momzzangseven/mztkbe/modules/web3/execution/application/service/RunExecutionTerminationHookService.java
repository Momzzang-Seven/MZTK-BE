package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTerminationEvidenceView;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.RunExecutionTerminationHookCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.RunExecutionTerminationHookUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunExecutionHookTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;

@RequiredArgsConstructor
public class RunExecutionTerminationHookService implements RunExecutionTerminationHookUseCase {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final List<ExecutionActionHandlerPort> executionActionHandlerPorts;
  private final RunExecutionHookTransactionPort transactionPort;

  @Override
  public void execute(RunExecutionTerminationHookCommand command) {
    ExecutionIntent intent =
        executionIntentPersistencePort
            .findByPublicId(command.executionIntentId())
            .orElseThrow(
                () ->
                    new Web3InvalidInputException(
                        "executionIntentId not found: " + command.executionIntentId()));
    ExecutionActionHandlerPort actionHandler = resolveActionHandler(intent);
    ExecutionActionPlan actionPlan = actionHandler.buildActionPlan(intent);
    ExecutionTerminationEvidenceView evidence =
        actionHandler.buildTerminationEvidence(
            intent, actionPlan, command.terminalStatus(), command.failureReason());
    transactionPort.requiresNew(
        () -> runTerminalStateHook(command, intent, actionHandler, actionPlan, evidence));
  }

  private void runTerminalStateHook(
      RunExecutionTerminationHookCommand command,
      ExecutionIntent intent,
      ExecutionActionHandlerPort actionHandler,
      ExecutionActionPlan actionPlan,
      ExecutionTerminationEvidenceView evidence) {
    if (command.terminalStatus() == ExecutionIntentStatus.FAILED_ONCHAIN) {
      actionHandler.afterExecutionFailedOnchain(intent, actionPlan, command.failureReason());
    }
    actionHandler.afterExecutionTerminated(
        intent, actionPlan, command.terminalStatus(), command.failureReason(), evidence);
  }

  private ExecutionActionHandlerPort resolveActionHandler(ExecutionIntent intent) {
    return ExecutionActionHandlerPort.findMatching(executionActionHandlerPorts, intent)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "no execution action handler for actionType=" + intent.getActionType()));
  }
}
