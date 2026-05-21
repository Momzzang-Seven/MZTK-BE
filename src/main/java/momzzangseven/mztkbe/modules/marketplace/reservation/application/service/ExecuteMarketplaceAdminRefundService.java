package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ExecuteMarketplaceAdminRefundCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionResult;

@RequiredArgsConstructor
public class ExecuteMarketplaceAdminRefundService {

  private final MarketplaceAdminExecutionOrchestrator orchestrator;

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
