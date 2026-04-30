package momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo;

import java.math.BigInteger;

/**
 * Domain event that triggers an escrow transaction dispatch AFTER the reservation DB state has been
 * committed.
 *
 * <p>Emitted by reservation state-transition services (Create, Reject, Cancel, Complete). The
 * listener {@code EscrowDispatchEventListener} picks it up via {@code AFTER_COMMIT} and calls the
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
 * @param delegationSignature EIP-7702 delegation signature (only for PURCHASE; null otherwise)
 * @param executionSignature EIP-7702 execution signature (only for PURCHASE; null otherwise)
 * @param amount token amount in wei (only for PURCHASE; null otherwise)
 */
public record EscrowDispatchEvent(
    Long reservationId,
    String orderId,
    EscrowAction action,
    String delegationSignature,
    String executionSignature,
    BigInteger amount) {

  /**
   * Convenience factory for non-PURCHASE actions (CANCEL / CONFIRM) that do not carry signatures or
   * amounts.
   */
  public static EscrowDispatchEvent of(Long reservationId, String orderId, EscrowAction action) {
    return new EscrowDispatchEvent(reservationId, orderId, action, null, null, null);
  }

  /**
   * Identifies which escrow contract function to call.
   *
   * <ul>
   *   <li>{@link #PURCHASE} — purchaseClass (new reservation path)
   *   <li>{@link #CANCEL} — cancelClass (trainer reject or user cancel path)
   *   <li>{@link #CONFIRM} — confirmClass (user settlement path)
   * </ul>
   */
  public enum EscrowAction {
    PURCHASE,
    CANCEL,
    CONFIRM
  }
}
