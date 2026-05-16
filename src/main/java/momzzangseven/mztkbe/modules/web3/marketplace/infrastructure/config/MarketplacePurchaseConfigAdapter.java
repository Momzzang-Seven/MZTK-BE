package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplacePurchaseConfigPort;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaRewardTokenProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnBean({MarketplaceEscrowProperties.class, QnaRewardTokenProperties.class})
public class MarketplacePurchaseConfigAdapter implements LoadMarketplacePurchaseConfigPort {

  private final MarketplaceEscrowProperties marketplaceEscrowProperties;
  private final QnaRewardTokenProperties qnaRewardTokenProperties;

  @Override
  public MarketplacePurchaseConfig loadPurchaseConfig() {
    var tokenConfig = qnaRewardTokenProperties.loadRewardTokenConfig();
    return new MarketplacePurchaseConfig(
        marketplaceEscrowProperties.getMarketplaceContractAddress(),
        tokenConfig.tokenContractAddress(),
        tokenConfig.decimals());
  }
}
