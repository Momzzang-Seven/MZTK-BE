package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import java.math.BigInteger;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link SubmitEscrowTransactionPort}.
 *
 * <p>The web3/escrow integration is owned by a separate developer. This stub returns a fake {@code
 * txHash} so that all reservation services can be developed and tested end-to-end without blocking
 * on the web3 module.
 *
 * <p><b>Active profiles:</b> {@code local}, {@code dev}, {@code test}. This bean is explicitly
 * excluded from the {@code prod} profile. Replace with a real cross-module adapter calling {@code
 * web3/application/port/in/SendEscrowTransactionUseCase} once the web3 module is ready.
 */
@Slf4j
@Component
@Profile({"local", "dev", "test", "integration"})
public class EscrowTransactionAdapter implements SubmitEscrowTransactionPort {

  private static final String STUB_TX_HASH =
      "0x0000000000000000000000000000000000000000000000000000000000000STUB";

  @Override
  public String submitPurchase(
      String orderId, String delegationSignature, String executionSignature, BigInteger amount) {
    log.warn("[STUB] submitPurchase: orderId={}, amount={}", orderId, amount);
    return STUB_TX_HASH;
  }

  @Override
  public String submitCancel(String orderId) {
    log.warn("[STUB] submitCancel: orderId={}", orderId);
    return STUB_TX_HASH;
  }

  @Override
  public String submitConfirm(String orderId) {
    log.warn("[STUB] submitConfirm: orderId={}", orderId);
    return STUB_TX_HASH;
  }

  @Override
  public String submitAdminRefund(String orderId) {
    log.warn("[STUB] submitAdminRefund: orderId={}", orderId);
    return STUB_TX_HASH;
  }

  @Override
  public String submitAdminSettle(String orderId) {
    log.warn("[STUB] submitAdminSettle: orderId={}", orderId);
    return STUB_TX_HASH;
  }
}
