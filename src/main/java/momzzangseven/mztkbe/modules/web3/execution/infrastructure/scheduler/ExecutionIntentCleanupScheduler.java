package momzzangseven.mztkbe.modules.web3.execution.infrastructure.scheduler;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.execution.application.service.ExecutionIntentCleanupService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduled cleanup for expired execution intents and sponsor daily usage rows. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "eip7702.cleanup.enabled"},
    havingValue = "true")
public class ExecutionIntentCleanupScheduler {

  private final ExecutionIntentCleanupService cleanupService;

  @Scheduled(cron = "${web3.eip7702.cleanup.cron}", zone = "${web3.eip7702.cleanup.zone}")
  public void run() {
    Instant now = Instant.now();
    int totalIntentExpired = 0;
    int totalIntentDeleted = 0;
    int totalUsageDeleted = 0;

    while (true) {
      ExecutionIntentCleanupService.CleanupBatchResult result = cleanupService.runBatch(now);
      if (result.totalDeleted() <= 0) {
        break;
      }
      totalIntentExpired += result.expiredExecutionIntent();
      totalIntentDeleted += result.deletedExecutionIntent();
      totalUsageDeleted += result.deletedDailyUsage();
    }

    if (totalIntentExpired > 0 || totalIntentDeleted > 0 || totalUsageDeleted > 0) {
      log.info(
          "execution intent cleanup completed: executionIntentExpired={}, executionIntentDeleted={}, sponsorDailyUsageDeleted={}",
          totalIntentExpired,
          totalIntentDeleted,
          totalUsageDeleted);
    }
  }
}
