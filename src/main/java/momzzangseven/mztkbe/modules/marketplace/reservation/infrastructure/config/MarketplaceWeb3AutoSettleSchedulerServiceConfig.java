package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.config;

import momzzangseven.mztkbe.global.config.ConditionalOnMarketplaceAdminEnabled;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ExecuteMarketplaceSchedulerAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RunMarketplaceWeb3AutoSettleBatchUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ScheduleMarketplaceWeb3AutoSettleCandidateUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ValidateMarketplaceWeb3AutoSettleConfigurationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.FindMarketplaceWeb3AutoSettleCandidatesPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadMarketplaceAdminExecutionAuthorityPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadMarketplaceWeb3InternalExecutionPolicyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCandidatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.MarketplaceWeb3AutoSettleBatchService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.ScheduleMarketplaceWeb3AutoSettleCandidateService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.ValidateMarketplaceWeb3AutoSettleConfigurationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    prefix = "web3.marketplace.admin.auto-settle",
    name = "enabled",
    havingValue = "true")
public class MarketplaceWeb3AutoSettleSchedulerServiceConfig {

  @Bean
  @ConditionalOnMarketplaceAdminEnabled
  ValidateMarketplaceWeb3AutoSettleConfigurationUseCase
      validateMarketplaceWeb3AutoSettleConfigurationUseCase(
          LoadMarketplaceWeb3InternalExecutionPolicyPort loadPolicyPort,
          LoadMarketplaceAdminExecutionAuthorityPort loadAuthorityPort) {
    return new ValidateMarketplaceWeb3AutoSettleConfigurationService(
        loadPolicyPort, loadAuthorityPort);
  }

  @Bean
  @ConditionalOnMarketplaceAdminEnabled
  ScheduleMarketplaceWeb3AutoSettleCandidateUseCase
      scheduleMarketplaceWeb3AutoSettleCandidateUseCase(
          ExecuteMarketplaceSchedulerAdminSettlementUseCase executeUseCase) {
    return new ScheduleMarketplaceWeb3AutoSettleCandidateService(executeUseCase);
  }

  @Bean
  @ConditionalOnMarketplaceAdminEnabled
  RunMarketplaceWeb3AutoSettleBatchUseCase runMarketplaceWeb3AutoSettleBatchUseCase(
      FindMarketplaceWeb3AutoSettleCandidatesPort findCandidatesPort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      LoadReservationExecutionCandidatePort loadReservationExecutionCandidatePort,
      ScheduleMarketplaceWeb3AutoSettleCandidateUseCase scheduleCandidateUseCase) {
    return new MarketplaceWeb3AutoSettleBatchService(
        findCandidatesPort,
        loadReservationExecutionStatePort,
        loadReservationExecutionCandidatePort,
        scheduleCandidateUseCase);
  }
}
