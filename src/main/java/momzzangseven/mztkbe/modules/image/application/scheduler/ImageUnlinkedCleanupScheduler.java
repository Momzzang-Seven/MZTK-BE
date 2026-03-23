package momzzangseven.mztkbe.modules.image.application.scheduler;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.image.application.config.ImageUnlinkedCleanupProperties;
import momzzangseven.mztkbe.modules.image.application.service.ImageUnlinkedCleanupService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler that triggers the unlinked image cleanup job.
 *
 * <p>Runs daily at 03:00 (Asia/Seoul). Iterates in batches until no more eligible rows remain,
 * preventing large single-transaction locks on the images table.
 *
 * <p>Covers all three unlinked-image sources:
 *
 * <ul>
 *   <li>Post deleted — images unlinked by {@code PostDeletedEventHandler}.
 *   <li>Post updated — removed images unlinked by {@code UpsertImagesByReferenceService}.
 *   <li>Presigned URL issued but post never created within the retention window.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(ImageUnlinkedCleanupProperties.class)
public class ImageUnlinkedCleanupScheduler {
  private final ImageUnlinkedCleanupService cleanupService;

  @Scheduled(
      cron = "${image.unlinked-cleanup.cron:0 0 3 * * *}",
      zone = "${image.unlinked-cleanup.zone:Asia/Seoul}")
  public void run() {
    Instant now = Instant.now();
    int totalDeleted = 0;

    while (true) {
      int deleted = cleanupService.runBatch(now);
      if (deleted <= 0) {
        break;
      }
      totalDeleted += deleted;
    }

    if (totalDeleted > 0) {
      log.info("Unlinked image cleanup job completed: totalDeleted={}", totalDeleted);
    }
  }
}
