package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.AutoSettleReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Batch service that auto-settles APPROVED reservations when the user fails to confirm within 24 h
 * after class end.
 *
 * <p><b>Transaction strategy:</b> {@code runBatch} runs in a read-only transaction for the
 * candidate query. Each item is delegated to {@link AutoSettleBatchItemProcessor#process} in its
 * own {@code REQUIRES_NEW} transaction, so a single on-chain or persistence failure does not roll
 * back already-settled items in the same run.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoSettleReservationService implements AutoSettleReservationUseCase {

  private static final int BATCH_SIZE = 50;

  private final LoadReservationPort loadReservationPort;

  /**
   * Handles per-item on-chain settle + DB update, each in a separate {@code REQUIRES_NEW}
   * transaction.
   */
  private final AutoSettleBatchItemProcessor itemProcessor;

  @Override
  @Transactional(readOnly = true)
  public int runBatch(LocalDateTime now) {
    List<Reservation> candidates = loadReservationPort.findApprovedForAutoSettle(now, BATCH_SIZE);

    if (candidates.isEmpty()) {
      return 0;
    }

    int processed = 0;
    for (Reservation reservation : candidates) {
      try {
        itemProcessor.process(reservation);
        processed++;
      } catch (Exception e) {
        log.error(
            "AutoSettle failed for reservationId={}: {}", reservation.getId(), e.getMessage(), e);
      }
    }
    return processed;
  }
}
