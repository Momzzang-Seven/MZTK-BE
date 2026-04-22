package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.TrainerStrikeEvent;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.dto.RecordTrainerStrikeCommand;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.in.RecordTrainerStrikeUseCase;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoCancelBatchItemProcessor {

  private final SaveReservationPort saveReservationPort;
  private final SubmitEscrowTransactionPort submitEscrowTransactionPort;
  private final RecordTrainerStrikeUseCase recordTrainerStrikeUseCase;

  /**
   * Processes a single auto-cancel item in its own isolated transaction.
   *
   * <p>On success: persists TIMEOUT_CANCELLED status, records a TIMEOUT strike. On failure: the
   * individual transaction rolls back; the caller catches and logs.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void process(Reservation reservation) {
    String refundTxHash = submitEscrowTransactionPort.submitAdminRefund(reservation.getOrderId());
    Reservation cancelled = reservation.timeoutCancel(refundTxHash);
    saveReservationPort.save(cancelled);
    recordTrainerStrikeUseCase.execute(
        new RecordTrainerStrikeCommand(
            reservation.getTrainerId(), TrainerStrikeEvent.REASON_TIMEOUT));
    log.info(
        "AutoCancel processed: reservationId={}, trainerId={}",
        reservation.getId(),
        reservation.getTrainerId());
  }
}
