package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config;

import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.PrecheckMarketplacePurchaseUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.PrepareMarketplaceUserExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.BuildMarketplaceUserExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceActiveWalletPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplacePurchaseConfigPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.PrecheckMarketplacePurchaseFundingPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.SubmitMarketplaceExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.service.PrecheckMarketplacePurchaseService;
import momzzangseven.mztkbe.modules.web3.marketplace.application.service.PrepareMarketplaceUserExecutionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
public class MarketplaceExecutionServiceConfig {

  @Bean
  @ConditionalOnBean({
    LoadMarketplaceActiveWalletPort.class,
    LoadMarketplacePurchaseConfigPort.class,
    PrecheckMarketplacePurchaseFundingPort.class
  })
  PrecheckMarketplacePurchaseUseCase precheckMarketplacePurchaseUseCase(
      LoadMarketplaceActiveWalletPort loadMarketplaceActiveWalletPort,
      LoadMarketplacePurchaseConfigPort loadMarketplacePurchaseConfigPort,
      PrecheckMarketplacePurchaseFundingPort precheckMarketplacePurchaseFundingPort) {
    return new PrecheckMarketplacePurchaseService(
        loadMarketplaceActiveWalletPort,
        loadMarketplacePurchaseConfigPort,
        precheckMarketplacePurchaseFundingPort);
  }

  @Bean
  @ConditionalOnBean({
    BuildMarketplaceUserExecutionDraftPort.class,
    SubmitMarketplaceExecutionDraftPort.class
  })
  PrepareMarketplaceUserExecutionUseCase prepareMarketplaceUserExecutionUseCase(
      BuildMarketplaceUserExecutionDraftPort buildDraftPort,
      SubmitMarketplaceExecutionDraftPort submitDraftPort) {
    return new PrepareMarketplaceUserExecutionService(buildDraftPort, submitDraftPort);
  }
}
