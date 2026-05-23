package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTerminationEvidenceView;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ReplayTerminatedExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ReplayTerminatedExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunExecutionHookTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;

@RequiredArgsConstructor
public class ReplayTerminatedExecutionIntentService
    implements ReplayTerminatedExecutionIntentUseCase {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final List<ExecutionActionHandlerPort> executionActionHandlerPorts;
  private final RunExecutionHookTransactionPort transactionPort;

  @Override
  public boolean execute(ReplayTerminatedExecutionIntentCommand command) {
    command.validate();
    ExecutionActionType expectedActionType = parseActionType(command.expectedActionType());
    ExecutionIntent intent =
        executionIntentPersistencePort
            .findByPublicId(command.executionIntentId())
            .filter(candidate -> candidate.getActionType() == expectedActionType)
            .filter(this::isReplayableTerminal)
            .orElse(null);
    if (intent == null) {
      return false;
    }

    ExecutionActionHandlerPort actionHandler = resolveActionHandler(intent);
    ExecutionActionPlan actionPlan = actionHandler.buildActionPlan(intent);
    String failureReason = failureReason(intent);
    ExecutionTerminationEvidenceView evidence =
        actionHandler.buildTerminationEvidence(
            intent, actionPlan, intent.getStatus(), failureReason);
    transactionPort.requiresNew(
        () ->
            runTerminalStateHook(
                intent, actionHandler, actionPlan, intent.getStatus(), failureReason, evidence));
    return true;
  }

  private boolean isReplayableTerminal(ExecutionIntent intent) {
    return intent.getStatus().isTerminal() && intent.getStatus() != ExecutionIntentStatus.CONFIRMED;
  }

  private ExecutionActionType parseActionType(String actionType) {
    try {
      return ExecutionActionType.valueOf(actionType);
    } catch (IllegalArgumentException e) {
      throw new Web3InvalidInputException("unsupported execution actionType: " + actionType);
    }
  }

  private void runTerminalStateHook(
      ExecutionIntent intent,
      ExecutionActionHandlerPort actionHandler,
      ExecutionActionPlan actionPlan,
      ExecutionIntentStatus terminalStatus,
      String failureReason,
      ExecutionTerminationEvidenceView evidence) {
    if (terminalStatus == ExecutionIntentStatus.FAILED_ONCHAIN) {
      actionHandler.afterExecutionFailedOnchain(intent, actionPlan, failureReason);
    }
    actionHandler.afterExecutionTerminated(
        intent, actionPlan, terminalStatus, failureReason, evidence);
  }

  private ExecutionActionHandlerPort resolveActionHandler(ExecutionIntent intent) {
    return ExecutionActionHandlerPort.findMatching(executionActionHandlerPorts, intent)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "no execution action handler for actionType=" + intent.getActionType()));
  }

  private String failureReason(ExecutionIntent intent) {
    if (intent.getLastErrorCode() != null && !intent.getLastErrorCode().isBlank()) {
      return intent.getLastErrorCode();
    }
    if (intent.getLastErrorReason() != null && !intent.getLastErrorReason().isBlank()) {
      return intent.getLastErrorReason();
    }
    return intent.getStatus().name();
  }
}
