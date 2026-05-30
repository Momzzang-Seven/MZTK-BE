package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceReservationStateException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ExecuteMarketplaceSchedulerAdminSettlementCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminSchedulerExecutionResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ExecuteMarketplaceSchedulerAdminSettlementUseCase;

@RequiredArgsConstructor
public class ExecuteMarketplaceSchedulerAdminSettlementService
    implements ExecuteMarketplaceSchedulerAdminSettlementUseCase {

  private final MarketplaceAdminExecutionOrchestrator orchestrator;

  @Override
  public MarketplaceAdminSchedulerExecutionResult execute(
      ExecuteMarketplaceSchedulerAdminSettlementCommand command) {
    command.validate();
    try {
      MarketplaceAdminExecutionResult result =
          orchestrator.executeSchedulerSettlement(
              command.schedulerRunId(), command.reasonCode(), command.reservationId());
      return MarketplaceAdminSchedulerExecutionResult.processed(result);
    } catch (MarketplaceReservationStateException e) {
      MarketplaceAdminReviewValidationCode skipCode =
          MarketplaceAdminSchedulerSkipCodeResolver.resolve(e);
      if (skipCode == null) {
        throw e;
      }
      return MarketplaceAdminSchedulerExecutionResult.skipped(skipCode.name(), e.getMessage());
    }
  }
}
