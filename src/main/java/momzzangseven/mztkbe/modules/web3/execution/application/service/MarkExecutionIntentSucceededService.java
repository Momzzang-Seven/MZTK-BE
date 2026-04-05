package momzzangseven.mztkbe.modules.web3.execution.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentSucceededUseCase;
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
public class MarkExecutionIntentSucceededService implements MarkExecutionIntentSucceededUseCase {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;

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
    if (intent.getStatus() == ExecutionIntentStatus.CONFIRMED) {
      return;
    }
    if (intent.getStatus() == ExecutionIntentStatus.PENDING_ONCHAIN) {
      executionIntentPersistencePort.update(intent.confirm());
      return;
    }
    if (intent.getStatus() == ExecutionIntentStatus.SIGNED) {
      executionIntentPersistencePort.update(
          intent.markPendingOnchain(intent.getSubmittedTxId()).confirm());
    }
  }
}
