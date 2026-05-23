package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.wallet;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceActiveWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetActiveWalletAddressUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnBean(GetActiveWalletAddressUseCase.class)
public class MarketplaceActiveWalletAdapter implements LoadMarketplaceActiveWalletPort {

  private final GetActiveWalletAddressUseCase getActiveWalletAddressUseCase;

  @Override
  public Optional<String> loadActiveWalletAddress(Long userId) {
    return getActiveWalletAddressUseCase.execute(userId);
  }
}
