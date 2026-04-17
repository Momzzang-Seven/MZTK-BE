package momzzangseven.mztkbe.modules.web3.execution.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.RunInternalExecutionBatchResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.RunInternalExecutionBatchUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled", "execution.internal-issuer.enabled"},
    havingValue = "true")
public class InternalExecutionIntentScheduler {

  private final RunInternalExecutionBatchUseCase runInternalExecutionBatchUseCase;

  @Scheduled(
      cron = "#{@internalExecutionIssuerProperties.cron}",
      zone = "#{@internalExecutionIssuerProperties.zone}")
  public void run() {
    try {
      int executedTotal = 0;
      int pendingTotal = 0;
      int signedTotal = 0;
      int quarantinedTotal = 0;
      int failedTotal = 0;

      while (true) {
        RunInternalExecutionBatchResult result =
            runInternalExecutionBatchUseCase.runBatch(java.time.Instant.now());
        executedTotal += result.executedCount();
        pendingTotal += result.pendingCount();
        signedTotal += result.signedCount();
        quarantinedTotal += result.quarantinedCount();
        failedTotal += result.failedCount();
        if (result.isEmpty() || result.failedCount() > 0) {
          break;
        }
      }

      if (executedTotal > 0
          || pendingTotal > 0
          || signedTotal > 0
          || quarantinedTotal > 0
          || failedTotal > 0) {
        log.info(
            "internal execution issuer completed: executed={}, pending={}, signed={}, quarantined={}, failed={}",
            executedTotal,
            pendingTotal,
            signedTotal,
            quarantinedTotal,
            failedTotal);
      }
    } catch (RuntimeException e) {
      log.error("internal execution issuer failed", e);
    }
  }
}
