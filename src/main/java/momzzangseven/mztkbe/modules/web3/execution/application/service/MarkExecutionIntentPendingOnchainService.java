package momzzangseven.mztkbe.modules.web3.execution.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentPendingOnchainUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class MarkExecutionIntentPendingOnchainService
    implements MarkExecutionIntentPendingOnchainUseCase {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;

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

    executionIntentPersistencePort.update(intent.markPendingOnchain(intent.getSubmittedTxId()));
    if (intent.getMode() == ExecutionMode.EIP7702
        && intent.getReservedSponsorCostWei().signum() > 0) {
      moveReservedToConsumed(intent);
    }
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
}
