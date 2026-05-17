package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetActiveWalletAddressUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
@ConditionalOnBean(GetActiveWalletAddressUseCase.class)
public class ReservationWalletAdapter implements LoadReservationWalletPort {

  private final GetActiveWalletAddressUseCase getActiveWalletAddressUseCase;

  @Override
  public Optional<String> loadActiveWalletAddress(Long userId) {
    return getActiveWalletAddressUseCase.execute(userId);
  }
}
