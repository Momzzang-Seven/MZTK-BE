package momzzangseven.mztkbe.modules.user.application.scheduler;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.user.application.service.WithdrawalHardDeleteService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawalHardDeleteScheduler {

  private final WithdrawalHardDeleteService hardDeleteService;

  /**
   * Hard delete soft-deleted users after retention.
   *
   * <p>Runs in batches to avoid long locks and excessive memory usage.
   */
  @Scheduled(cron = "${withdrawal.hard-delete.cron}", zone = "${withdrawal.hard-delete.zone}")
  public void run() {
    LocalDateTime now = LocalDateTime.now();
    int totalDeleted = 0;

    while (true) {
      int deleted = hardDeleteService.runBatch(now);
      if (deleted <= 0) {
        break;
      }
      totalDeleted += deleted;
    }

    if (totalDeleted > 0) {
      log.info("Withdrawal hard delete job completed: deletedUsers={}", totalDeleted);
    }
  }
}
