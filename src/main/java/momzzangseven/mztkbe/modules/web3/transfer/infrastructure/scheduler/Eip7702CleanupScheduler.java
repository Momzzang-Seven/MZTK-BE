package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.scheduler;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.transfer.application.service.Eip7702CleanupService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduled cleanup for EIP-7702 transfer prepare/daily-usage data. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "eip7702.cleanup.enabled"},
    havingValue = "true")
public class Eip7702CleanupScheduler {

  private final Eip7702CleanupService cleanupService;

  @Scheduled(cron = "${web3.eip7702.cleanup.cron}", zone = "${web3.eip7702.cleanup.zone}")
  public void run() {
    Instant now = Instant.now();
    int totalPrepareDeleted = 0;
    int totalUsageDeleted = 0;

    while (true) {
      Eip7702CleanupService.CleanupBatchResult result = cleanupService.runBatch(now);
      if (result.totalDeleted() <= 0) {
        break;
      }
      totalPrepareDeleted += result.deletedPrepare();
      totalUsageDeleted += result.deletedDailyUsage();
    }

    if (totalPrepareDeleted > 0 || totalUsageDeleted > 0) {
      log.info(
          "EIP-7702 cleanup completed: prepareDeleted={}, sponsorDailyUsageDeleted={}",
          totalPrepareDeleted,
          totalUsageDeleted);
    }
  }
}
