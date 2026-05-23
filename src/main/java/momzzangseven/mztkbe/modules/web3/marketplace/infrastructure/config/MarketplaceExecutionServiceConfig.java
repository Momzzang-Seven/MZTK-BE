package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config;

import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.FilterMarketplaceExecutionCleanupCandidatesUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.GetMarketplaceEscrowOrderUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.PrecheckMarketplacePurchaseUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.PrepareMarketplaceUserExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.BuildMarketplaceUserExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.CheckMarketplaceReservationCleanupProtectionPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceEscrowOrderPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceExecutionCleanupIntentPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplacePurchaseConfigPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.PrecheckMarketplacePurchaseFundingPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.SubmitMarketplaceExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.service.GetMarketplaceEscrowOrderService;
import momzzangseven.mztkbe.modules.web3.marketplace.application.service.MarketplaceExecutionCleanupProtectionService;
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
    LoadMarketplacePurchaseConfigPort.class,
    PrecheckMarketplacePurchaseFundingPort.class
  })
  PrecheckMarketplacePurchaseUseCase precheckMarketplacePurchaseUseCase(
      LoadMarketplacePurchaseConfigPort loadMarketplacePurchaseConfigPort,
      PrecheckMarketplacePurchaseFundingPort precheckMarketplacePurchaseFundingPort) {
    return new PrecheckMarketplacePurchaseService(
        loadMarketplacePurchaseConfigPort, precheckMarketplacePurchaseFundingPort);
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

  @Bean
  @ConditionalOnBean(LoadMarketplaceEscrowOrderPort.class)
  GetMarketplaceEscrowOrderUseCase getMarketplaceEscrowOrderUseCase(
      LoadMarketplaceEscrowOrderPort loadMarketplaceEscrowOrderPort) {
    return new GetMarketplaceEscrowOrderService(loadMarketplaceEscrowOrderPort);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "web3",
      name = {"eip7702.enabled", "eip7702.cleanup.enabled"},
      havingValue = "true")
  @ConditionalOnBean({
    LoadMarketplaceExecutionCleanupIntentPort.class,
    CheckMarketplaceReservationCleanupProtectionPort.class
  })
  FilterMarketplaceExecutionCleanupCandidatesUseCase
      filterMarketplaceExecutionCleanupCandidatesUseCase(
          LoadMarketplaceExecutionCleanupIntentPort loadMarketplaceExecutionCleanupIntentPort,
          CheckMarketplaceReservationCleanupProtectionPort
              checkMarketplaceReservationCleanupProtectionPort) {
    return new MarketplaceExecutionCleanupProtectionService(
        loadMarketplaceExecutionCleanupIntentPort,
        checkMarketplaceReservationCleanupProtectionPort);
  }
}
