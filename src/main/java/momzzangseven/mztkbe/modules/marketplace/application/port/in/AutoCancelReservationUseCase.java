package momzzangseven.mztkbe.modules.marketplace.application.port.in;

import java.time.LocalDateTime;

/**
 * Input port for the auto-cancel scheduler batch job.
 *
 * <p>Processes PENDING reservations that have timed out (72 h since creation, or 1 h before session
 * start) and transitions them to TIMEOUT_CANCELLED.
 */
public interface AutoCancelReservationUseCase {
  /**
   * Process one batch of auto-cancellable reservations.
   *
   * @param now current server time
   * @return number of reservations cancelled in this batch; 0 means no more candidates
   */
  int runBatch(LocalDateTime now);
}
