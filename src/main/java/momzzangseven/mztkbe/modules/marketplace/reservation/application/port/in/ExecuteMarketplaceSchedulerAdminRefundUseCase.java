package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ExecuteMarketplaceSchedulerAdminRefundCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminSchedulerExecutionResult;

/** Non-audited scheduler entry point for marketplace admin refund execution. */
public interface ExecuteMarketplaceSchedulerAdminRefundUseCase {

  MarketplaceAdminSchedulerExecutionResult execute(
      ExecuteMarketplaceSchedulerAdminRefundCommand command);
}
