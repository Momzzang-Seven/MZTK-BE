package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.AutoCancelReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Batch service that auto-cancels PENDING reservations due to trainer inactivity.
 *
 * <p>Conditions (OR): created_at older than 72 h, OR session starts within 1 h.
 *
 * <p><b>Transaction strategy:</b> {@code runBatch} runs in a read-only transaction for the
 * candidate query. Each item is then delegated to {@link AutoCancelBatchItemProcessor#process} in
 * its own {@code REQUIRES_NEW} transaction. This prevents a single JPA/network failure from rolling
 * back already-processed items within the same batch run.
 *
 * <p>Best-effort policy: failed items are logged and skipped; the next scheduler run will retry
 * them as long as they still satisfy the cancellation conditions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoCancelReservationService implements AutoCancelReservationUseCase {

  private static final int BATCH_SIZE = 50;
  private static final long TIMEOUT_HOURS = 72L;
  private static final long SESSION_WINDOW_HOURS = 1L;

  private final LoadReservationPort loadReservationPort;

  /**
   * Handles per-item on-chain refund + DB update + strike recording, each in a separate {@code
   * REQUIRES_NEW} transaction.
   */
  private final AutoCancelBatchItemProcessor itemProcessor;

  @Override
  @Transactional(readOnly = true)
  public int runBatch(LocalDateTime now) {
    LocalDateTime nowMinusTimeout = now.minusHours(TIMEOUT_HOURS);
    LocalDateTime nowPlusWindow = now.plusHours(SESSION_WINDOW_HOURS);

    List<Reservation> candidates =
        loadReservationPort.findPendingForAutoCancel(nowMinusTimeout, nowPlusWindow, BATCH_SIZE);

    if (candidates.isEmpty()) {
      return 0;
    }

    int processed = 0;
    for (Reservation reservation : candidates) {
      try {
        itemProcessor.process(reservation);
        processed++;
      } catch (Exception e) {
        // Best-effort: log and continue so one bad item does not block the batch.
        // TODO: dead-letter queue or alert for repeated failures on the same reservationId.
        log.error(
            "AutoCancel failed for reservationId={}: {}", reservation.getId(), e.getMessage(), e);
      }
    }
    return processed;
  }
}
