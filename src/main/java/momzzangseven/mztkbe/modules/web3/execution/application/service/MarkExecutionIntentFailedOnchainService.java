package momzzangseven.mztkbe.modules.web3.execution.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentFailedOnchainUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
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
public class MarkExecutionIntentFailedOnchainService
    implements MarkExecutionIntentFailedOnchainUseCase {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;

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
      executionIntentPersistencePort.update(
          intent.failOnchain(
              "FAILED_ONCHAIN", failureReason == null ? "FAILED_ONCHAIN" : failureReason));
    }
  }
}
