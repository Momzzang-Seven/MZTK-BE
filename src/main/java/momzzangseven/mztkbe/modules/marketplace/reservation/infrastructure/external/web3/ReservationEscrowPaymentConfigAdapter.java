package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReservationEscrowPaymentConfigAdapter
    implements LoadReservationEscrowPaymentConfigPort {

  @Value("${web3.reward-token.token-contract-address}")
  private String tokenContractAddress;

  @Value("${web3.reward-token.decimals}")
  private int decimals;

  @Value("${web3.escrow.default-deadline-duration-seconds:604800}")
  private long defaultDeadlineDurationSeconds;

  @Override
  public ReservationEscrowPaymentConfig load() {
    return new ReservationEscrowPaymentConfig(
        tokenContractAddress, decimals, defaultDeadlineDurationSeconds);
  }
}
