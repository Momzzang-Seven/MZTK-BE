package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link SubmitEscrowTransactionPort}.
 *
 * <p>The web3/escrow integration is owned by a separate developer. This stub returns a fake {@code
 * txHash} so that all reservation services can be developed and tested end-to-end without blocking
 * on the web3 module.
 *
 * <p><b>Note:</b> Active in all profiles. A {@link PostConstruct} guard will throw {@link
 * IllegalStateException} on startup if the active profile is {@code prod}, ensuring this stub is
 * never silently used in production. Replace with a real cross-module adapter calling {@code
 * web3/application/port/in/SendEscrowTransactionUseCase} once the web3 module is ready.
 */
@Slf4j
@Component
public class EscrowTransactionAdapter implements SubmitEscrowTransactionPort {

  private static final String STUB_TX_HASH =
      "0x0000000000000000000000000000000000000000000000000000000000000STUB";

  private final Environment environment;

  public EscrowTransactionAdapter(Environment environment) {
    this.environment = environment;
  }

  @PostConstruct
  void rejectUnsafeRuntime() {
    boolean prodProfile = environment.acceptsProfiles(Profiles.of("prod"));
    boolean marketplaceAdminEnabled =
        environment.getProperty("web3.marketplace.admin.enabled", Boolean.class, false);
    if (prodProfile || marketplaceAdminEnabled) {
      throw new IllegalStateException(
          "[STUB] EscrowTransactionAdapter must not run when prod profile or marketplace admin "
              + "execution is enabled. Replace the legacy stub before enabling marketplace admin.");
    }
    log.warn(
        "[STUB] EscrowTransactionAdapter is active (profiles={}). "
            + "All legacy escrow calls return a fake txHash.",
        String.join(",", environment.getActiveProfiles()));
  }

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
