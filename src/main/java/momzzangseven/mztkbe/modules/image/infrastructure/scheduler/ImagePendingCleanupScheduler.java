package momzzangseven.mztkbe.modules.image.infrastructure.scheduler;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.image.application.port.in.RunPendingImageCleanupBatchUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Driving adapter that triggers the orphaned PENDING image cleanup job via cron.
 *
 * <p>Runs at every hour. Iterates in batches until no more eligible rows remain, preventing large
 * single-transaction locks on the images table.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImagePendingCleanupScheduler {

  private final RunPendingImageCleanupBatchUseCase cleanupUseCase;

  @Scheduled(
      cron = "${image.pending-cleanup.cron:0 0 0/1 * * *}",
      zone = "${image.pending-cleanup.zone:Asia/Seoul}")
  public void run() {
    Instant now = Instant.now();
    int totalDeleted = 0;

    while (true) {
      int deleted = cleanupUseCase.runBatch(now);
      if (deleted <= 0) {
        break;
      }
      totalDeleted += deleted;
    }

    if (totalDeleted > 0) {
      log.info("Orphaned PENDING image cleanup job completed: totalDeleted={}", totalDeleted);
    }
  }
}
