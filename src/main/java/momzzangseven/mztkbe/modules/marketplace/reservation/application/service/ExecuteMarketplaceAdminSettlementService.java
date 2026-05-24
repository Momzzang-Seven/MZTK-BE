package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ExecuteMarketplaceAdminSettlementCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ExecuteMarketplaceAdminSettlementUseCase;

@RequiredArgsConstructor
public class ExecuteMarketplaceAdminSettlementService
    implements ExecuteMarketplaceAdminSettlementUseCase {

  private final MarketplaceAdminExecutionOrchestrator orchestrator;

  @Override
  public MarketplaceAdminExecutionResult execute(ExecuteMarketplaceAdminSettlementCommand command) {
    command.validate();
    return orchestrator.executeSettlement(
        command.operatorId(),
        command.reasonCode(),
        command.memo(),
        command.canEarlySettle(),
        command.reservationId());
  }
}
