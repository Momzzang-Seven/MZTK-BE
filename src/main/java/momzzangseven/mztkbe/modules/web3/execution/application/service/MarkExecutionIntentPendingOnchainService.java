package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentPendingOnchainUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunAfterCommitPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import org.springframework.transaction.annotation.Transactional;

/** Synchronizes execution intent state to {@code PENDING_ONCHAIN} from tx worker callbacks. */
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MarkExecutionIntentPendingOnchainService
    implements MarkExecutionIntentPendingOnchainUseCase {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  private final List<ExecutionActionHandlerPort> executionActionHandlerPorts;
  private final RunAfterCommitPort runAfterCommitPort;
  private final Clock appClock;

  /** Marks the linked intent as pending on-chain when the submitted tx starts pending state. */
  @Override
  public void execute(Long submittedTxId) {
    if (submittedTxId == null || submittedTxId <= 0) {
      return;
    }

    executionIntentPersistencePort
        .findBySubmittedTxIdForUpdate(submittedTxId)
        .ifPresent(this::markPendingIfNecessary);
  }

  private void markPendingIfNecessary(ExecutionIntent intent) {
    if (intent.getStatus() != ExecutionIntentStatus.SIGNED) {
      return;
    }

    ExecutionIntent pendingIntent =
        executionIntentPersistencePort.update(
            intent.markPendingOnchain(intent.getSubmittedTxId(), LocalDateTime.now(appClock)));
    if (intent.getMode() == ExecutionMode.EIP7702
        && intent.getReservedSponsorCostWei().signum() > 0) {
      moveReservedToConsumed(intent);
    }
    scheduleAfterTransactionSubmittedHook(pendingIntent);
  }

  private void moveReservedToConsumed(ExecutionIntent intent) {
    SponsorDailyUsage usage =
        sponsorDailyUsagePersistencePort.getOrCreateForUpdate(
            intent.getRequesterUserId(), intent.resolveSponsorUsageDateKst());

    sponsorDailyUsagePersistencePort.update(
        usage
            .release(intent.getReservedSponsorCostWei())
            .consume(intent.getReservedSponsorCostWei()));
  }

  private void scheduleAfterTransactionSubmittedHook(ExecutionIntent pendingIntent) {
    runAfterCommitPort.runAfterCommit(() -> afterTransactionSubmittedSafely(pendingIntent));
  }

  private void afterTransactionSubmittedSafely(ExecutionIntent pendingIntent) {
    try {
      runAfterTransactionSubmitted(pendingIntent);
    } catch (RuntimeException ex) {
      log.error(
          "Execution intent pending but post-submit sync failed: executionIntentId={}, actionType={}",
          pendingIntent.getPublicId(),
          pendingIntent.getActionType(),
          ex);
    }
  }

  private void runAfterTransactionSubmitted(ExecutionIntent pendingIntent) {
    ExecutionActionHandlerPort.findMatching(executionActionHandlerPorts, pendingIntent)
        .ifPresentOrElse(
            actionHandler -> {
              ExecutionActionPlan actionPlan = actionHandler.buildActionPlan(pendingIntent);
              actionHandler.afterTransactionSubmitted(
                  pendingIntent, actionPlan, ExecutionTransactionStatus.PENDING);
            },
            () ->
                log.warn(
                    "Skipping execution action submission hook because handler was not found:"
                        + " executionIntentId={}, actionType={}",
                    pendingIntent.getPublicId(),
                    pendingIntent.getActionType()));
  }
}
