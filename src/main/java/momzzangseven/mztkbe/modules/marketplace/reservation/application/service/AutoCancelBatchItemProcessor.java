package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RecordTrainerStrikePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.TrainerStrikeEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-item transaction processor for the auto-cancel batch job.
 *
 * <p>Each reservation is processed in its own {@code REQUIRES_NEW} transaction. This isolates
 * failures: if on-chain refund or strike recording fails for one reservation, the others in the
 * same scheduler run are not rolled back.
 *
 * <p>This class must be a separate Spring bean (not a private inner class) because Spring AOP
 * proxy-based transaction management only intercepts calls that cross bean boundaries.
 *
 * <p><b>Transaction ordering (DB-first, escrow-after):</b><br>
 * The reservation status is persisted first. Then the on-chain {@code adminRefund} call is made.
 * If the escrow call fails, the status remains TIMEOUT_CANCELLED in DB (correct terminal state)
 * and the strike is still recorded. The missing on-chain refund can be resolved by a reconciliation
 * job. This prevents the inverse failure (on-chain success + DB rollback) which would be harder to
 * detect and recover from.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoCancelBatchItemProcessor {

  private final SaveReservationPort saveReservationPort;
  private final SubmitEscrowTransactionPort submitEscrowTransactionPort;
  private final RecordTrainerStrikePort recordTrainerStrikePort;

  /**
   * Processes a single auto-cancel item in its own isolated transaction.
   *
   * <p>Order: persist TIMEOUT_CANCELLED → submit adminRefund on-chain → update txHash → record
   * TIMEOUT strike. On failure: the individual transaction rolls back; the caller catches and logs.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void process(Reservation reservation) {
    // 1. Persist status first — DB is the source of truth.
    Reservation cancelled = reservation.timeoutCancel("ESCROW_DISPATCH_PENDING");
    saveReservationPort.save(cancelled);

    // 2. Submit on-chain refund — after DB commit in REQUIRES_NEW, this is effectively post-commit.
    String refundTxHash = submitEscrowTransactionPort.submitAdminRefund(reservation.getOrderId());

    // 3. Write back the real txHash.
    Reservation withTxHash = cancelled.updateTxHash(refundTxHash);
    saveReservationPort.save(withTxHash);

    // 4. Record trainer strike.
    recordTrainerStrikePort.recordStrike(
        reservation.getTrainerId(), TrainerStrikeEvent.REASON_TIMEOUT);

    log.info(
        "AutoCancel processed: reservationId={}, trainerId={}",
        reservation.getId(),
        reservation.getTrainerId());
  }
}
