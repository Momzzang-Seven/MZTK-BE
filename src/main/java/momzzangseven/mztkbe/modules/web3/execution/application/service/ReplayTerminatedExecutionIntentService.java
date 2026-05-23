package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTerminationEvidenceView;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ReplayTerminatedExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ReplayTerminatedExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunExecutionHookTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;

@RequiredArgsConstructor
public class ReplayTerminatedExecutionIntentService
    implements ReplayTerminatedExecutionIntentUseCase {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final LoadExecutionTransactionPort loadExecutionTransactionPort;
  private final List<ExecutionActionHandlerPort> executionActionHandlerPorts;
  private final RunExecutionHookTransactionPort transactionPort;
  private final Clock appClock;

  @Override
  public boolean execute(ReplayTerminatedExecutionIntentCommand command) {
    ExecutionIntent intent = resolveReplayTarget(command);
    return intent != null && replayTerminated(intent);
  }

  public ExecutionIntent resolveReplayTarget(ReplayTerminatedExecutionIntentCommand command) {
    command.validate();
    ExecutionActionType expectedActionType = parseActionType(command.expectedActionType());
    return executionIntentPersistencePort
        .findByPublicIdForUpdate(command.executionIntentId())
        .filter(candidate -> candidate.getActionType() == expectedActionType)
        .flatMap(this::replayableTerminalOrRepairableFailedOnchain)
        .orElse(null);
  }

  public boolean replayTerminated(ExecutionIntent intent) {
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

  private Optional<ExecutionIntent> replayableTerminalOrRepairableFailedOnchain(
      ExecutionIntent intent) {
    if (intent.getStatus().isTerminal() && intent.getStatus() != ExecutionIntentStatus.CONFIRMED) {
      return Optional.of(intent);
    }
    if (!intent.getStatus().isInFlight() || intent.getSubmittedTxId() == null) {
      return Optional.empty();
    }
    return loadExecutionTransactionPort
        .findById(intent.getSubmittedTxId())
        .filter(transaction -> transaction.status() == ExecutionTransactionStatus.FAILED_ONCHAIN)
        .map(transaction -> markFailedFromFailedOnchainTransaction(intent, transaction));
  }

  private ExecutionIntent markFailedFromFailedOnchainTransaction(
      ExecutionIntent intent, ExecutionTransactionSummary transaction) {
    ExecutionIntent failed =
        intent.failOnchain(
            ExecutionIntentStatus.FAILED_ONCHAIN.name(),
            "transaction " + transaction.transactionId() + " failed onchain",
            LocalDateTime.now(appClock));
    return executionIntentPersistencePort.update(failed);
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
