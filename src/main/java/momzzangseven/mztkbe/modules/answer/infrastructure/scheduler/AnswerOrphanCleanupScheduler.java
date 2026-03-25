package momzzangseven.mztkbe.modules.answer.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.answer.application.port.in.RunOrphanAnswerCleanupBatchUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Driving adapter that deletes orphan answers left behind after asynchronous post-delete failures.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerOrphanCleanupScheduler {

  private final RunOrphanAnswerCleanupBatchUseCase cleanupUseCase;

  @Scheduled(
      cron = "${answer.orphan-cleanup.cron:0 30 * * * *}",
      zone = "${answer.orphan-cleanup.zone:Asia/Seoul}")
  public void run() {
    int totalDeleted = 0;

    while (true) {
      int deleted = cleanupUseCase.runBatch();
      if (deleted <= 0) {
        break;
      }
      totalDeleted += deleted;
    }

    if (totalDeleted > 0) {
      log.info("Orphan answer cleanup job completed: totalDeleted={}", totalDeleted);
    }
  }
}
