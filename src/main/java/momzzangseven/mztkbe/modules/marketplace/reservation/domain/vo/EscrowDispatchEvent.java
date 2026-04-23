package momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo;

/**
 * Domain event that triggers an escrow transaction dispatch AFTER the reservation DB state has been
 * committed.
 *
 * <p>Emitted by reservation state-transition services (Reject, Cancel, Complete). The listener
 * {@code EscrowDispatchEventListener} picks it up via {@code AFTER_COMMIT} and calls the
 * appropriate {@code SubmitEscrowTransactionPort} method, then updates the {@code txHash} column in
 * a follow-up transaction.
 *
 * <p>Using {@code AFTER_COMMIT} guarantees that on-chain transactions are only submitted after the
 * DB row is durably committed, preventing the "on-chain success + DB rollback" divergence that
 * occurs when escrow is called inside the originating transaction.
 *
 * @param reservationId ID of the reservation whose state just changed
 * @param orderId the escrow contract's order identifier
 * @param action which escrow operation to invoke
 */
public record EscrowDispatchEvent(Long reservationId, String orderId, EscrowAction action) {

  /**
   * Identifies which escrow contract function to call.
   *
   * <ul>
   *   <li>{@link #CANCEL} — cancelClass (trainer reject or user cancel path)
   *   <li>{@link #CONFIRM} — confirmClass (user settlement path)
   * </ul>
   */
  public enum EscrowAction {
    CANCEL,
    CONFIRM
  }
}
