package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.math.BigInteger;

/**
 * Output port for submitting escrow smart-contract transactions.
 *
 * <p>All methods return the on-chain transaction hash ({@code txHash}).
 */
public interface SubmitEscrowTransactionPort {

  /**
   * Relay a {@code purchaseClass} transaction using EIP-7702 delegation.
   *
   * @param orderId server-generated UUID identifying this order in the contract
   * @param delegationSignature EIP-7702 authorisation tuple signed by the user
   * @param executionSignature {@code purchaseClass} transaction signed by the user
   * @param amount token amount (must match the class price)
   * @return on-chain transaction hash
   */
  String submitPurchase(
      String orderId, String delegationSignature, String executionSignature, BigInteger amount);

  /**
   * Relay a {@code cancelClass} transaction (user cancel or trainer reject path).
   *
   * @param orderId the escrow order to cancel
   * @return on-chain transaction hash
   */
  String submitCancel(String orderId);

  /**
   * Relay a {@code confirmClass} transaction to settle funds to the trainer.
   *
   * @param orderId the escrow order to confirm
   * @return on-chain transaction hash
   */
  String submitConfirm(String orderId);

  /**
   * Relay an {@code adminRefund} transaction to force-refund the user after trainer inactivity.
   *
   * @param orderId the escrow order to admin-refund
   * @return on-chain transaction hash
   */
  String submitAdminRefund(String orderId);

  /**
   * Relay an {@code adminSettle} transaction to force-pay the trainer after user no-show.
   *
   * @param orderId the escrow order to admin-settle
   * @return on-chain transaction hash
   */
  String submitAdminSettle(String orderId);
}
