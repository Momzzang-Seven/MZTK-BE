package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ReplayConfirmedExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ReplayConfirmedExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;

@RequiredArgsConstructor
public class ReplayConfirmedExecutionIntentService
    implements ReplayConfirmedExecutionIntentUseCase {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final List<ExecutionActionHandlerPort> executionActionHandlerPorts;

  @Override
  public boolean execute(ReplayConfirmedExecutionIntentCommand command) {
    command.validate();
    ExecutionActionType expectedActionType = parseActionType(command.expectedActionType());
    return executionIntentPersistencePort
        .findByPublicId(command.executionIntentId())
        .filter(intent -> isReplayable(intent, expectedActionType))
        .map(this::replayConfirmed)
        .orElse(false);
  }

  private ExecutionActionType parseActionType(String actionType) {
    try {
      return ExecutionActionType.valueOf(actionType);
    } catch (IllegalArgumentException e) {
      throw new Web3InvalidInputException("unsupported execution actionType: " + actionType);
    }
  }

  private boolean isReplayable(ExecutionIntent intent, ExecutionActionType expectedActionType) {
    return intent.getStatus() == ExecutionIntentStatus.CONFIRMED
        && intent.getActionType() == expectedActionType;
  }

  private boolean replayConfirmed(ExecutionIntent intent) {
    ExecutionActionHandlerPort actionHandler = resolveActionHandler(intent);
    ExecutionActionPlan actionPlan = actionHandler.buildActionPlan(intent);
    actionHandler.afterExecutionConfirmed(intent, actionPlan);
    return true;
  }

  private ExecutionActionHandlerPort resolveActionHandler(ExecutionIntent intent) {
    return executionActionHandlerPorts.stream()
        .filter(handler -> handler.supports(intent.getActionType()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "no execution action handler for actionType=" + intent.getActionType()));
  }
}
