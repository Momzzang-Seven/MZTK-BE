package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config;

import momzzangseven.mztkbe.global.config.ConditionalOnMarketplaceAdminEnabled;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.BuildMarketplaceAdminExecutionDraftUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.LoadMarketplaceAdminExecutionAuthorityUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.SubmitMarketplaceAdminExecutionDraftUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.BuildMarketplaceAdminExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.CheckMarketplaceAdminRelayerRegistrationPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceAdminSignerWalletPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.SubmitMarketplaceAdminExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.VerifyMarketplaceAdminSignerWalletPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.service.BuildMarketplaceAdminExecutionDraftService;
import momzzangseven.mztkbe.modules.web3.marketplace.application.service.LoadMarketplaceAdminExecutionAuthorityService;
import momzzangseven.mztkbe.modules.web3.marketplace.application.service.SubmitMarketplaceAdminExecutionDraftService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnMarketplaceAdminEnabled
public class MarketplaceAdminExecutionServiceConfig {

  @Bean
  @ConditionalOnBean(BuildMarketplaceAdminExecutionDraftPort.class)
  BuildMarketplaceAdminExecutionDraftUseCase buildMarketplaceAdminExecutionDraftUseCase(
      BuildMarketplaceAdminExecutionDraftPort buildMarketplaceAdminExecutionDraftPort) {
    return new BuildMarketplaceAdminExecutionDraftService(buildMarketplaceAdminExecutionDraftPort);
  }

  @Bean
  @ConditionalOnBean(SubmitMarketplaceAdminExecutionDraftPort.class)
  SubmitMarketplaceAdminExecutionDraftUseCase submitMarketplaceAdminExecutionDraftUseCase(
      SubmitMarketplaceAdminExecutionDraftPort submitMarketplaceAdminExecutionDraftPort) {
    return new SubmitMarketplaceAdminExecutionDraftService(
        submitMarketplaceAdminExecutionDraftPort);
  }

  @Bean
  @ConditionalOnBean({
    LoadMarketplaceAdminSignerWalletPort.class,
    VerifyMarketplaceAdminSignerWalletPort.class,
    CheckMarketplaceAdminRelayerRegistrationPort.class
  })
  LoadMarketplaceAdminExecutionAuthorityUseCase loadMarketplaceAdminExecutionAuthorityUseCase(
      LoadMarketplaceAdminSignerWalletPort loadMarketplaceAdminSignerWalletPort,
      VerifyMarketplaceAdminSignerWalletPort verifyMarketplaceAdminSignerWalletPort,
      CheckMarketplaceAdminRelayerRegistrationPort checkMarketplaceAdminRelayerRegistrationPort) {
    return new LoadMarketplaceAdminExecutionAuthorityService(
        loadMarketplaceAdminSignerWalletPort,
        verifyMarketplaceAdminSignerWalletPort,
        checkMarketplaceAdminRelayerRegistrationPort);
  }
}
