package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ExecuteMarketplaceSchedulerAdminSettlementCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminSchedulerExecutionResult;

/** Non-audited scheduler entry point for marketplace admin settlement execution. */
public interface ExecuteMarketplaceSchedulerAdminSettlementUseCase {

  MarketplaceAdminSchedulerExecutionResult execute(
      ExecuteMarketplaceSchedulerAdminSettlementCommand command);
}
