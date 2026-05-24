package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceWeb3DisabledException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;

/**
 * Disabled legacy direct escrow submission adapter.
 *
 * <p>Marketplace execution is now handled through execution intents. This adapter keeps legacy
 * scheduler wiring explicit without returning fake tx hashes.
 */
public class LegacyEscrowTransactionDisabledAdapter implements SubmitEscrowTransactionPort {

  @Override
  public String submitPurchase(
      String orderId, String delegationSignature, String executionSignature, BigInteger amount) {
    throw disabled();
  }

  @Override
  public String submitCancel(String orderId) {
    throw disabled();
  }

  @Override
  public String submitConfirm(String orderId) {
    throw disabled();
  }

  @Override
  public String submitAdminRefund(String orderId) {
    throw disabled();
  }

  @Override
  public String submitAdminSettle(String orderId) {
    throw disabled();
  }

  private static MarketplaceWeb3DisabledException disabled() {
    return new MarketplaceWeb3DisabledException();
  }
}
