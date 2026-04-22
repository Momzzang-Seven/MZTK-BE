package momzzangseven.mztkbe.modules.marketplace.infrastructure.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.AutoCancelReservationUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler (Driving Adapter) that periodically invokes the auto-cancel batch use case.
 *
 * <p>Runs every 30 minutes (configurable via {@code marketplace.reservation.auto-cancel.cron}).
 * Loops until the use case returns 0 to handle large backlogs in a single run.
 *
 * <p>Uses an injected {@link Clock} so that the "current time" is testable and timezone-aware
 * ({@code Asia/Seoul} by default via {@code TimeConfig}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoCancelReservationScheduler {

  private final AutoCancelReservationUseCase autoCancelReservationUseCase;

  /**
   * Injected clock for testable, timezone-aware "now" computation.
   *
   * <p>In production this is bound to {@code Asia/Seoul} by the {@code @Bean Clock} in {@code
   * TimeConfig}. In tests, a fixed clock can be substituted via the constructor.
   */
  private final Clock clock;

  @Scheduled(
      cron = "${marketplace.reservation.auto-cancel.cron:0 0/30 * * * *}",
      zone = "${marketplace.reservation.zone:Asia/Seoul}")
  public void run() {
    log.debug("AutoCancelReservationScheduler started");
    int totalCancelled = 0;
    while (true) {
      int n = autoCancelReservationUseCase.runBatch(LocalDateTime.now(clock));
      if (n <= 0) break;
      totalCancelled += n;
    }
    if (totalCancelled > 0) {
      log.info(
          "AutoCancelReservationScheduler completed: {} reservations cancelled", totalCancelled);
    }
  }
}
