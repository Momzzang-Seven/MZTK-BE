package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaRewardTokenProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationEscrowPaymentConfigAdapter
    implements LoadReservationEscrowPaymentConfigPort {

  private final QnaRewardTokenProperties rewardTokenProperties;

  @Value("${web3.escrow.default-deadline-duration-seconds:604800}")
  private long defaultDeadlineDurationSeconds;

  @Override
  public ReservationEscrowPaymentConfig load() {
    var config = rewardTokenProperties.loadRewardTokenConfig();
    return new ReservationEscrowPaymentConfig(
        config.tokenContractAddress(), config.decimals(), defaultDeadlineDurationSeconds);
  }
}
