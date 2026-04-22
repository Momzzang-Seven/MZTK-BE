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
   * <p>On success: persists AUTO_SETTLED status and the on-chain txHash.
   * On failure: the individual transaction rolls back; the caller catches and logs.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void process(Reservation reservation) {
    String settleTxHash =
        submitEscrowTransactionPort.submitAdminSettle(reservation.getOrderId());
    Reservation settled = reservation.autoSettle(settleTxHash);
    saveReservationPort.save(settled);
    log.info(
        "AutoSettle processed: reservationId={}, trainerId={}",
        reservation.getId(),
        reservation.getTrainerId());
  }
}
