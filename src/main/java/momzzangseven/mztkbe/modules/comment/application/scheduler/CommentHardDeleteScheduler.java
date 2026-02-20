package momzzangseven.mztkbe.modules.comment.application.scheduler;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.comment.application.service.CommentHardDeleteService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommentHardDeleteScheduler {

  private final CommentHardDeleteService commentHardDeleteService;

  @Scheduled(
      cron = "${comment.hard-delete.cron:0 0 4 * * *}",
      zone = "${comment.hard-delete.zone:Asia/Seoul}")
  public void run() {
    LocalDateTime now = LocalDateTime.now();
    int totalDeleted = 0;

    while (true) {
      int deleted = commentHardDeleteService.runBatch(now);
      if (deleted <= 0) {
        break;
      }
      totalDeleted += deleted;
    }

    if (totalDeleted > 0) {
      log.info("Comment hard delete job completed: deletedComments={}", totalDeleted);
    }
  }
}
