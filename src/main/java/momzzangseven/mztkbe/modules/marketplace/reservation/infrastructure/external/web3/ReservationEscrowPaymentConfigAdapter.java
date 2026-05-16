package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config.MarketplaceRewardTokenProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationEscrowPaymentConfigAdapter
    implements LoadReservationEscrowPaymentConfigPort {

  private final MarketplaceRewardTokenProperties rewardTokenProperties;

  @Value("${web3.escrow.default-deadline-duration-seconds:604800}")
  private long defaultDeadlineDurationSeconds;

  @Override
  public ReservationEscrowPaymentConfig load() {
    return new ReservationEscrowPaymentConfig(
        rewardTokenProperties.getTokenContractAddress(),
        rewardTokenProperties.getDecimals(),
        defaultDeadlineDurationSeconds);
  }
}
