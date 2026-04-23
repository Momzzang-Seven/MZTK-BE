package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-item transaction processor for the auto-settle batch job.
 *
 * <p>Each reservation is settled in its own {@code REQUIRES_NEW} transaction so that a single
 * on-chain or persistence failure does not prevent other candidates from being settled in the same
 * scheduler run.
 *
 * <p>Must be a separate Spring bean to cross the proxy boundary required for AOP transaction
 * interception.
 *
 * <p><b>Transaction ordering (DB-first, escrow-after):</b><br>
 * The reservation status is persisted as AUTO_SETTLED first. Then the on-chain {@code adminSettle}
 * call is made and the txHash is written back. If the escrow call fails, the DB status is still
 * AUTO_SETTLED (correct terminal state) and the missing txHash can be reconciled separately.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoSettleBatchItemProcessor {

  private final SaveReservationPort saveReservationPort;
  private final SubmitEscrowTransactionPort submitEscrowTransactionPort;

  /**
   * Settles a single approved reservation in its own isolated transaction.
   *
   * <p>Order: persist AUTO_SETTLED → submit adminSettle on-chain → update txHash. On failure: the
   * individual transaction rolls back; the caller catches and logs.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void process(Reservation reservation) {
    // 1. Persist status first.
    Reservation settled = reservation.autoSettle("ESCROW_DISPATCH_PENDING");
    saveReservationPort.save(settled);

    // 2. Submit on-chain settle — after DB save in REQUIRES_NEW.
    String settleTxHash = submitEscrowTransactionPort.submitAdminSettle(reservation.getOrderId());

    // 3. Write back the real txHash.
    Reservation withTxHash = settled.updateTxHash(settleTxHash);
    saveReservationPort.save(withTxHash);

    log.info(
        "AutoSettle processed: reservationId={}, trainerId={}",
        reservation.getId(),
        reservation.getTrainerId());
  }
}
