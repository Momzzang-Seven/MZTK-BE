package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentFailedOnchainUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional
@Slf4j
/** Marks execution intent as failed-onchain from transaction outcome callbacks. */
public class MarkExecutionIntentFailedOnchainService
    implements MarkExecutionIntentFailedOnchainUseCase {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final List<ExecutionActionHandlerPort> executionActionHandlerPorts;
  private final Clock appClock;

  /** Applies idempotent failed-onchain transition for intent linked by submitted tx id. */
  @Override
  public void execute(Long submittedTxId, String failureReason) {
    if (submittedTxId == null || submittedTxId <= 0) {
      return;
    }

    executionIntentPersistencePort
        .findBySubmittedTxIdForUpdate(submittedTxId)
        .ifPresent(intent -> markFailedIfNecessary(intent, failureReason));
  }

  private void markFailedIfNecessary(ExecutionIntent intent, String failureReason) {
    if (intent.getStatus() == ExecutionIntentStatus.FAILED_ONCHAIN) {
      return;
    }
    if (intent.getStatus() == ExecutionIntentStatus.PENDING_ONCHAIN
        || intent.getStatus() == ExecutionIntentStatus.SIGNED) {
      String lastErrorReason = persistedFailureReason(failureReason);
      ExecutionIntent failedIntent =
          intent.failOnchain(
              ExecutionIntentStatus.FAILED_ONCHAIN.name(),
              lastErrorReason,
              LocalDateTime.now(appClock));
      executionIntentPersistencePort.update(failedIntent);
      afterExecutionFailedOnchainSafely(failedIntent, failureReason);
    }
  }

  private String persistedFailureReason(String failureReason) {
    if (failureReason == null || failureReason.isBlank()) {
      return ExecutionIntentStatus.FAILED_ONCHAIN.name();
    }
    return failureReason;
  }

  private void afterExecutionFailedOnchainSafely(ExecutionIntent intent, String failureReason) {
    try {
      ExecutionActionHandlerPort handler = resolveActionHandler(intent);
      var actionPlan = handler.buildActionPlan(intent);
      handler.afterExecutionFailedOnchain(intent, actionPlan, failureReason);
      handler.afterExecutionTerminated(
          intent, actionPlan, ExecutionIntentStatus.FAILED_ONCHAIN, failureReason);
    } catch (RuntimeException ex) {
      log.error(
          "Execution intent failed onchain but failure sync failed: executionIntentId={}, actionType={}",
          intent.getPublicId(),
          intent.getActionType(),
          ex);
    }
  }

  private ExecutionActionHandlerPort resolveActionHandler(ExecutionIntent intent) {
    return executionActionHandlerPorts.stream()
        .filter(candidate -> candidate.supports(intent.getActionType()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "unsupported execution action: " + intent.getActionType()));
  }
}
