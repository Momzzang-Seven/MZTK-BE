package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CancelExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CancelExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.PublishExecutionIntentTerminatedPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.event.ExecutionIntentTerminatedEvent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnUserExecutionEnabled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnUserExecutionEnabled
public class CancelExecutionIntentService implements CancelExecutionIntentUseCase {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  private final PublishExecutionIntentTerminatedPort publishExecutionIntentTerminatedPort;
  private final Clock appClock;

  @Override
  @Transactional
  public boolean cancelIfSignable(CancelExecutionIntentCommand command) {
    command.validate();
    return executionIntentPersistencePort
        .findByPublicIdForUpdate(command.executionIntentId())
        .filter(intent -> intent.getStatus().isSignable())
        .map(intent -> cancel(intent, command))
        .orElse(false);
  }

  private boolean cancel(ExecutionIntent intent, CancelExecutionIntentCommand command) {
    releaseReservedSponsorExposure(intent);
    ExecutionIntent canceled =
        executionIntentPersistencePort.update(
            intent.cancel(
                command.resolvedErrorCode(),
                command.resolvedErrorReason(),
                LocalDateTime.now(appClock)));
    publishExecutionIntentTerminatedPort.publish(
        new ExecutionIntentTerminatedEvent(
            canceled.getPublicId(), ExecutionIntentStatus.CANCELED, command.resolvedErrorCode()));
    return true;
  }

  private void releaseReservedSponsorExposure(ExecutionIntent intent) {
    if (intent.getReservedSponsorCostWei() == null
        || intent.getReservedSponsorCostWei().signum() <= 0) {
      return;
    }
    sponsorDailyUsagePersistencePort
        .findForUpdate(intent.getRequesterUserId(), intent.resolveSponsorUsageDateKst())
        .ifPresent(
            usage ->
                sponsorDailyUsagePersistencePort.update(
                    usage.release(intent.getReservedSponsorCostWei())));
  }
}
