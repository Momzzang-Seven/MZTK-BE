package momzzangseven.mztkbe.modules.marketplace.application.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.application.dto.RecordTrainerStrikeCommand;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.AutoCancelReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.RecordTrainerStrikeUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.Reservation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Batch service that auto-cancels PENDING reservations due to trainer inactivity.
 *
 * <p>Conditions (OR): created_at older than 72 h, OR session starts within 1 h.
 *
 * <p>Strike recording uses {@link RecordTrainerStrikeUseCase} (input port) — the same interface
 * used by {@link momzzangseven.mztkbe.modules.marketplace.infrastructure.event.ReservationSanctionEventListener}.
 * This keeps the two cancel paths (trainer-explicit reject vs scheduler timeout) consistent: both
 * go through an input port instead of touching {@code ManageTrainerSanctionPort} (output port)
 * directly.
 *
 * <p>Each reservation is processed individually inside a try-catch (best-effort policy). A single
 * failed item does not abort the remainder of the batch.
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

  /**
   * Input port for recording trainer strikes.
   *
   * <p>Using an input port (not {@code ManageTrainerSanctionPort} output port directly) keeps this
   * service consistent with the event-listener-driven reject path and satisfies ARCHITECTURE.md:
   * "application/service must not call another module's output port directly."
   */
  private final RecordTrainerStrikeUseCase recordTrainerStrikeUseCase;

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

        // Record TIMEOUT strike via input port — same path as ReservationSanctionEventListener
        recordTrainerStrikeUseCase.execute(
            new RecordTrainerStrikeCommand(reservation.getTrainerId(), "TIMEOUT"));

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
