package momzzangseven.mztkbe.modules.marketplace.application.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.AutoCancelReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.ManageTrainerSanctionPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.Reservation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Batch service that auto-cancels PENDING reservations due to trainer inactivity.
 *
 * <p>Conditions: created_at older than 72 h OR session starts within 1 h. Calls adminRefund
 * on-chain and records a TIMEOUT strike.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoCancelReservationService implements AutoCancelReservationUseCase {

  private static final int BATCH_SIZE = 50;
  private static final long TIMEOUT_HOURS = 72L;
  private static final long SESSION_WINDOW_HOURS = 1L;

  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final SubmitEscrowTransactionPort submitEscrowTransactionPort;
  private final ManageTrainerSanctionPort manageTrainerSanctionPort;

  @Override
  @Transactional
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
        String refundTxHash =
            submitEscrowTransactionPort.submitAdminRefund(reservation.getOrderId());
        Reservation cancelled = reservation.timeoutCancel(refundTxHash);
        saveReservationPort.save(cancelled);
        manageTrainerSanctionPort.recordStrike(reservation.getTrainerId(), "TIMEOUT");
        processed++;
        log.info(
            "AutoCancel: reservationId={}, trainerId={}",
            reservation.getId(),
            reservation.getTrainerId());
      } catch (Exception e) {
        // Best-effort policy: log and continue to avoid a single failure blocking the batch.
        // Retryable errors (e.g. transient network) will be retried in the next scheduler run.
        // Non-retryable errors (e.g. invalid orderId) will surface as repeated log.error entries.
        // TODO: consider a Dead Letter Queue or alerting mechanism for repeated failures.
        log.error(
            "AutoCancel failed for reservationId={}: {}", reservation.getId(), e.getMessage(), e);
      }
    }
    return processed;
  }
}
