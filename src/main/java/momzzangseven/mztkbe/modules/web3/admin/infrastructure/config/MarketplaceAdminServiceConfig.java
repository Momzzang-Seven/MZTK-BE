package momzzangseven.mztkbe.modules.web3.admin.infrastructure.config;

import momzzangseven.mztkbe.global.config.ConditionalOnMarketplaceAdminEnabled;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceMarketplaceAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceMarketplaceAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetMarketplaceAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetMarketplaceAdminSettlementReviewUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ForceMarketplaceAdminRefundPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ForceMarketplaceAdminSettlementPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.GetMarketplaceAdminRefundReviewPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.GetMarketplaceAdminSettlementReviewPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ResolveMarketplaceAdminAuthorityPort;
import momzzangseven.mztkbe.modules.web3.admin.application.service.ForceMarketplaceAdminRefundService;
import momzzangseven.mztkbe.modules.web3.admin.application.service.ForceMarketplaceAdminSettlementService;
import momzzangseven.mztkbe.modules.web3.admin.application.service.GetMarketplaceAdminRefundReviewService;
import momzzangseven.mztkbe.modules.web3.admin.application.service.GetMarketplaceAdminSettlementReviewService;
import momzzangseven.mztkbe.modules.web3.admin.application.service.MarketplaceAdminAuthorityPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnMarketplaceAdminEnabled
public class MarketplaceAdminServiceConfig {

  @Bean
  GetMarketplaceAdminRefundReviewUseCase getMarketplaceAdminRefundReviewUseCase(
      GetMarketplaceAdminRefundReviewPort port,
      ResolveMarketplaceAdminAuthorityPort authorityPort) {
    return new GetMarketplaceAdminRefundReviewService(port, authorityPort);
  }

  @Bean
  GetMarketplaceAdminSettlementReviewUseCase getMarketplaceAdminSettlementReviewUseCase(
      GetMarketplaceAdminSettlementReviewPort port,
      ResolveMarketplaceAdminAuthorityPort authorityPort) {
    return new GetMarketplaceAdminSettlementReviewService(port, authorityPort);
  }

  @Bean
  ForceMarketplaceAdminRefundUseCase forceMarketplaceAdminRefundUseCase(
      ForceMarketplaceAdminRefundPort port, ResolveMarketplaceAdminAuthorityPort authorityPort) {
    return new ForceMarketplaceAdminRefundService(port, authorityPort);
  }

  @Bean
  ForceMarketplaceAdminSettlementUseCase forceMarketplaceAdminSettlementUseCase(
      ForceMarketplaceAdminSettlementPort port,
      ResolveMarketplaceAdminAuthorityPort authorityPort) {
    return new ForceMarketplaceAdminSettlementService(port, authorityPort);
  }

  @Bean
  MarketplaceAdminAuthorityPolicy marketplaceAdminAuthorityPolicy() {
    return new MarketplaceAdminAuthorityPolicy();
  }
}
