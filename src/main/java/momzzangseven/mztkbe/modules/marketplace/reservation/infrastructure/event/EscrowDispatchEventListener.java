package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.EscrowDispatchEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Infrastructure listener that submits escrow transactions AFTER the reservation DB row has been
 * committed.
 *
 * <p><b>Why AFTER_COMMIT?</b><br>
 * Calling escrow inside the originating transaction creates a split-brain risk: if the on-chain
 * call succeeds but the DB commit subsequently fails (network hiccup, constraint violation, etc.),
 * the blockchain state is mutated while the DB still shows the old status. By deferring to
 * AFTER_COMMIT, we guarantee the DB row is durable before any on-chain side-effect is triggered.
 *
 * <p><b>txHash write-back:</b><br>
 * The reservation row is initially saved with a sentinel {@code txHash} value ({@value
 * PENDING_TX_HASH}). This listener calls escrow, receives the real txHash, then updates the row in
 * a fresh {@code REQUIRES_NEW} transaction.
 *
 * <p><b>Failure handling:</b><br>
 * If escrow submission or the txHash write-back fails, the DB status is already committed and the
 * reservation lifecycle is correct. The missing txHash is an operational concern that can be
 * resolved by a reconciliation job or manual re-submission. Errors are logged at ERROR level for
 * alerting.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EscrowDispatchEventListener {

  /**
   * Sentinel txHash written to the DB before the real on-chain call. Signals that escrow dispatch
   * is pending. A reconciliation job can query rows with this value to detect stuck dispatches.
   */
  public static final String PENDING_TX_HASH = "ESCROW_DISPATCH_PENDING";

  private final SubmitEscrowTransactionPort submitEscrowTransactionPort;
  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(EscrowDispatchEvent event) {
    try {
      String realTxHash = dispatch(event);
      updateTxHash(event.reservationId(), realTxHash);
    } catch (Exception e) {
      log.error(
          "[EscrowDispatch] Failed for reservationId={}, orderId={}, action={}: {}",
          event.reservationId(),
          event.orderId(),
          event.action(),
          e.getMessage(),
          e);
      // Do NOT rethrow — DB state is already committed and correct.
      // A reconciliation job should detect PENDING_TX_HASH rows and retry.
    }
  }

  // -----------------------------------------------------------------------
  // Private helpers
  // -----------------------------------------------------------------------

  private String dispatch(EscrowDispatchEvent event) {
    return switch (event.action()) {
      case PURCHASE ->
          submitEscrowTransactionPort.submitPurchase(
              event.orderId(),
              event.delegationSignature(),
              event.executionSignature(),
              event.amount());
      case CANCEL -> submitEscrowTransactionPort.submitCancel(event.orderId());
      case CONFIRM -> submitEscrowTransactionPort.submitConfirm(event.orderId());
    };
  }

  private void updateTxHash(Long reservationId, String realTxHash) {
    Reservation reservation =
        loadReservationPort
            .findByIdWithLock(reservationId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "[EscrowDispatch] Reservation disappeared after commit: " + reservationId));

    Reservation updated = reservation.updateTxHash(realTxHash);
    saveReservationPort.save(updated);

    log.info(
        "[EscrowDispatch] txHash written: reservationId={}, txHash={}", reservationId, realTxHash);
  }
}
