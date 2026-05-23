package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ExecuteMarketplaceAdminRefundCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionResult;

public interface ExecuteMarketplaceAdminRefundUseCase {

  MarketplaceAdminExecutionResult execute(ExecuteMarketplaceAdminRefundCommand command);
}
