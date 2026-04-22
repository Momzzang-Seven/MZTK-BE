package momzzangseven.mztkbe.modules.marketplace.application.port.in;

import java.time.LocalDateTime;

/**
 * Input port for the auto-settle scheduler batch job.
 *
 * <p>Processes APPROVED reservations where the class ended more than 24 h ago without user
 * confirmation. Transitions them to AUTO_SETTLED.
 */
public interface AutoSettleReservationUseCase {
  /**
   * Process one batch of auto-settleable reservations.
   *
   * @param now current server time
   * @return number of reservations settled in this batch; 0 means no more candidates
   */
  int runBatch(LocalDateTime now);
}
