package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ReplayConfirmedExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ReplayConfirmedExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunAfterCommitPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional
@Slf4j
public class ReplayConfirmedExecutionIntentService
    implements ReplayConfirmedExecutionIntentUseCase {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final LoadExecutionTransactionPort loadExecutionTransactionPort;
  private final List<ExecutionActionHandlerPort> executionActionHandlerPorts;
  private final RunAfterCommitPort runAfterCommitPort;
  private final Clock appClock;

  @Override
  public boolean execute(ReplayConfirmedExecutionIntentCommand command) {
    command.validate();
    ExecutionActionType expectedActionType = parseActionType(command.expectedActionType());
    return executionIntentPersistencePort
        .findByPublicIdForUpdate(command.executionIntentId())
        .filter(intent -> intent.getActionType() == expectedActionType)
        .flatMap(this::confirmedOrRepairable)
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

  private Optional<ExecutionIntent> confirmedOrRepairable(ExecutionIntent intent) {
    if (intent.getStatus() == ExecutionIntentStatus.CONFIRMED) {
      return Optional.of(intent);
    }
    if (intent.getSubmittedTxId() == null
        || (intent.getStatus() != ExecutionIntentStatus.PENDING_ONCHAIN
            && intent.getStatus() != ExecutionIntentStatus.SIGNED)) {
      return Optional.empty();
    }
    return loadExecutionTransactionPort
        .findById(intent.getSubmittedTxId())
        .filter(transaction -> transaction.status() == ExecutionTransactionStatus.SUCCEEDED)
        .map(transaction -> markConfirmedFromSucceededTransaction(intent, transaction));
  }

  private ExecutionIntent markConfirmedFromSucceededTransaction(
      ExecutionIntent intent, ExecutionTransactionSummary transaction) {
    LocalDateTime now = LocalDateTime.now(appClock);
    ExecutionIntent confirmed =
        intent.getStatus() == ExecutionIntentStatus.SIGNED
            ? intent.markPendingOnchain(transaction.transactionId(), now).confirm(now)
            : intent.confirm(now);
    return executionIntentPersistencePort.update(confirmed);
  }

  private boolean replayConfirmed(ExecutionIntent intent) {
    runAfterCommitPort.runAfterCommit(() -> afterExecutionConfirmedSafely(intent));
    return true;
  }

  private void afterExecutionConfirmedSafely(ExecutionIntent intent) {
    try {
      ExecutionActionHandlerPort handler = resolveActionHandler(intent);
      handler.afterExecutionConfirmed(intent, handler.buildActionPlan(intent));
    } catch (RuntimeException ex) {
      log.error(
          "Execution intent replay confirmed but post-confirm sync failed: "
              + "executionIntentId={}, actionType={}",
          intent.getPublicId(),
          intent.getActionType(),
          ex);
    }
  }

  private ExecutionActionHandlerPort resolveActionHandler(ExecutionIntent intent) {
    return ExecutionActionHandlerPort.findMatching(executionActionHandlerPorts, intent)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "no execution action handler for actionType=" + intent.getActionType()));
  }
}
