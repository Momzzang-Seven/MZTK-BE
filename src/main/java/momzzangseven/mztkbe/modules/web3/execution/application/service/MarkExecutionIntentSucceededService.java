package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
/** Marks execution intent as confirmed when transaction outcome reports success. */
public class MarkExecutionIntentSucceededService implements MarkExecutionIntentSucceededUseCase {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final List<ExecutionActionHandlerPort> executionActionHandlerPorts;
  private final Clock appClock;

  /** Applies idempotent success transition for intent bound to submitted transaction id. */
  @Override
  public void execute(Long submittedTxId) {
    if (submittedTxId == null || submittedTxId <= 0) {
      return;
    }

    executionIntentPersistencePort
        .findBySubmittedTxIdForUpdate(submittedTxId)
        .ifPresent(this::markSucceededIfNecessary);
  }

  private void markSucceededIfNecessary(ExecutionIntent intent) {
    LocalDateTime now = LocalDateTime.now(appClock);
    if (intent.getStatus() == ExecutionIntentStatus.CONFIRMED) {
      return;
    }
    if (intent.getStatus() == ExecutionIntentStatus.PENDING_ONCHAIN) {
      ExecutionIntent confirmed = intent.confirm(now);
      executionIntentPersistencePort.update(confirmed);
      afterExecutionConfirmedSafely(confirmed);
      return;
    }
    if (intent.getStatus() == ExecutionIntentStatus.SIGNED) {
      ExecutionIntent confirmed =
          intent.markPendingOnchain(intent.getSubmittedTxId(), now).confirm(now);
      executionIntentPersistencePort.update(confirmed);
      afterExecutionConfirmedSafely(confirmed);
    }
  }

  private void afterExecutionConfirmedSafely(ExecutionIntent intent) {
    try {
      ExecutionActionHandlerPort handler = resolveActionHandler(intent);
      handler.afterExecutionConfirmed(intent, handler.buildActionPlan(intent));
    } catch (RuntimeException ex) {
      log.error(
          "Execution intent confirmed but post-confirm sync failed: executionIntentId={}, actionType={}",
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
