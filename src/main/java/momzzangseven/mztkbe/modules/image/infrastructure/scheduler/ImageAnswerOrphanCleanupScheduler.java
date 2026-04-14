package momzzangseven.mztkbe.modules.image.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.image.application.port.in.RunOrphanAnswerImageCleanupBatchUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Driving adapter that unlinks images still pointing at deleted answers. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageAnswerOrphanCleanupScheduler {

  private final RunOrphanAnswerImageCleanupBatchUseCase cleanupUseCase;

  @Scheduled(
      cron = "${image.answer-orphan-cleanup.cron:0 45 * * * *}",
      zone = "${image.answer-orphan-cleanup.zone:Asia/Seoul}")
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
      log.info("Orphan answer image cleanup job completed: totalUnlinked={}", totalUnlinked);
    }
  }
}
