package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ExecuteMarketplaceAdminRefundCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ExecuteMarketplaceAdminRefundUseCase;

@RequiredArgsConstructor
public class ExecuteMarketplaceAdminRefundService implements ExecuteMarketplaceAdminRefundUseCase {

  private final MarketplaceAdminExecutionOrchestrator orchestrator;

  @Override
  public MarketplaceAdminExecutionResult execute(ExecuteMarketplaceAdminRefundCommand command) {
    command.validate();
    return orchestrator.executeRefund(
        command.operatorId(),
        command.reasonCode(),
        command.memo(),
        command.canManualRefund(),
        command.reservationId());
  }
}
