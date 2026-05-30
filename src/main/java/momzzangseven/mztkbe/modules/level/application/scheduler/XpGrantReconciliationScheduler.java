package momzzangseven.mztkbe.modules.level.application.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.level.application.config.XpGrantReconciliationProperties;
import momzzangseven.mztkbe.modules.level.application.dto.RunXpGrantReconciliationCommand;
import momzzangseven.mztkbe.modules.level.application.dto.RunXpGrantReconciliationResult;
import momzzangseven.mztkbe.modules.level.application.port.in.RunXpGrantReconciliationUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Driving adapter that retries failed XP grants from the outbox on a schedule. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "level.xp-reconciliation", name = "enabled", havingValue = "true")
public class XpGrantReconciliationScheduler {

  private final RunXpGrantReconciliationUseCase reconciliationUseCase;
  private final XpGrantReconciliationProperties properties;
  private final AtomicBoolean running = new AtomicBoolean(false);

  @Scheduled(cron = "${level.xp-reconciliation.cron}", zone = "${level.xp-reconciliation.zone}")
  public void run() {
    if (!running.compareAndSet(false, true)) {
      log.warn("XP grant reconciliation skipped: reason=already-running");
      return;
    }
    try {
      RunXpGrantReconciliationResult result =
          reconciliationUseCase.run(
              new RunXpGrantReconciliationCommand(
                  properties.getBatchSize(),
                  properties.getMaxAttempts(),
                  properties.getBackoffSeconds()));
      if (result.scanned() > 0) {
        log.info(
            "XP grant reconciliation completed: scanned={}, granted={}, skipped={}, failed={}",
            result.scanned(),
            result.granted(),
            result.skipped(),
            result.failed());
      }
    } finally {
      running.set(false);
    }
  }
}
