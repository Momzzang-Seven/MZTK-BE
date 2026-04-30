package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.AutoSettleReservationUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler (Driving Adapter) that periodically invokes the auto-settle batch use case.
 *
 * <p>Runs every hour (configurable via {@code marketplace.reservation.auto-settle.cron}). Loops
 * until the use case returns 0 to handle large backlogs in a single run.
 *
 * <p>Uses an injected {@link Clock} so that the "current time" is testable and timezone-aware
 * ({@code Asia/Seoul} by default via {@code TimeConfig}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoSettleReservationScheduler {

  private final AutoSettleReservationUseCase autoSettleReservationUseCase;

  /**
   * Injected clock for testable, timezone-aware "now" computation.
   *
   * <p>In production this is bound to {@code Asia/Seoul} by the {@code @Bean Clock} in {@code
   * TimeConfig}. In tests, a fixed clock can be substituted via the constructor.
   */
  private final Clock clock;

  @Scheduled(
      cron = "${marketplace.reservation.auto-settle.cron:0 0 * * * *}",
      zone = "${marketplace.reservation.zone:Asia/Seoul}")
  public void runSettle() {
    log.debug("AutoSettleReservationScheduler started");
    int totalSettled = 0;
    while (true) {
      int n = autoSettleReservationUseCase.runBatch(LocalDateTime.now(clock));
      if (n <= 0) break;
      totalSettled += n;
    }
    if (totalSettled > 0) {
      log.info("AutoSettleReservationScheduler completed: {} reservations settled", totalSettled);
    }
  }
}
