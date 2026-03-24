package momzzangseven.mztkbe.modules.image.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.image.application.port.in.RunOrphanPostImageCleanupBatchUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Driving adapter that unlinks images still pointing at deleted posts. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImagePostOrphanCleanupScheduler {

  private final RunOrphanPostImageCleanupBatchUseCase cleanupUseCase;

  @Scheduled(
      cron = "${image.post-orphan-cleanup.cron:0 40 * * * *}",
      zone = "${image.post-orphan-cleanup.zone:Asia/Seoul}")
  public void run() {
    int totalUnlinked = 0;

    while (true) {
      int unlinked = cleanupUseCase.runBatch();
      if (unlinked <= 0) {
        break;
      }
      totalUnlinked += unlinked;
    }

    if (totalUnlinked > 0) {
      log.info("Orphan post image cleanup job completed: totalUnlinked={}", totalUnlinked);
    }
  }
}
