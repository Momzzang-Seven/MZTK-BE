package momzzangseven.mztkbe.modules.comment.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.comment.application.port.in.RunOrphanAnswerCommentCleanupBatchUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Driving adapter that repeatedly runs orphan answer comment cleanup until no work remains. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentAnswerOrphanCleanupScheduler {

  private final RunOrphanAnswerCommentCleanupBatchUseCase cleanupUseCase;

  /** Runs the scheduled orphan answer comment cleanup job. */
  @Scheduled(
      cron = "${comment.answer-orphan-cleanup.cron:0 35 * * * *}",
      zone = "${comment.answer-orphan-cleanup.zone:Asia/Seoul}")
  public void run() {
    int totalSoftDeleted = 0;

    while (true) {
      int softDeleted = cleanupUseCase.runBatch();
      if (softDeleted <= 0) {
        break;
      }
      totalSoftDeleted += softDeleted;
    }

    if (totalSoftDeleted > 0) {
      log.info("Orphan answer comment cleanup job completed: softDeleted={}", totalSoftDeleted);
    }
  }
}
