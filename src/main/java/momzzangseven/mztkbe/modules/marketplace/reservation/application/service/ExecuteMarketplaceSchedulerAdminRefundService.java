package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceReservationStateException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ExecuteMarketplaceSchedulerAdminRefundCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminSchedulerExecutionResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ExecuteMarketplaceSchedulerAdminRefundUseCase;

@RequiredArgsConstructor
public class ExecuteMarketplaceSchedulerAdminRefundService
    implements ExecuteMarketplaceSchedulerAdminRefundUseCase {

  private final MarketplaceAdminExecutionOrchestrator orchestrator;

  @Override
  public MarketplaceAdminSchedulerExecutionResult execute(
      ExecuteMarketplaceSchedulerAdminRefundCommand command) {
    command.validate();
    try {
      MarketplaceAdminExecutionResult result =
          orchestrator.executeSchedulerRefund(
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
