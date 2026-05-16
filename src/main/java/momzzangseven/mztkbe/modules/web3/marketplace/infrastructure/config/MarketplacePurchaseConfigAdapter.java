package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplacePurchaseConfigPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnBean({MarketplaceEscrowProperties.class, MarketplaceRewardTokenProperties.class})
public class MarketplacePurchaseConfigAdapter implements LoadMarketplacePurchaseConfigPort {

  private final MarketplaceEscrowProperties marketplaceEscrowProperties;
  private final MarketplaceRewardTokenProperties rewardTokenProperties;

  @Override
  public MarketplacePurchaseConfig loadPurchaseConfig() {
    return new MarketplacePurchaseConfig(
        marketplaceEscrowProperties.getMarketplaceContractAddress(),
        rewardTokenProperties.getTokenContractAddress(),
        rewardTokenProperties.getDecimals());
  }
}
