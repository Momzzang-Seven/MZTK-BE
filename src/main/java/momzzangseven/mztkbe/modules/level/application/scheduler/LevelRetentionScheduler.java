package momzzangseven.mztkbe.modules.level.application.scheduler;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.level.application.port.in.PurgeLevelDataUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LevelRetentionScheduler {

  private final PurgeLevelDataUseCase purgeLevelDataUseCase;

  @Scheduled(cron = "${level.retention.cron}", zone = "${level.retention.zone:Asia/Seoul}")
  public void run() {
    LocalDateTime now = LocalDateTime.now();
    int deleted = purgeLevelDataUseCase.execute(now);
    if (deleted > 0) {
      log.info("Level retention purge job completed: deletedRows={}", deleted);
    }
  }
}
