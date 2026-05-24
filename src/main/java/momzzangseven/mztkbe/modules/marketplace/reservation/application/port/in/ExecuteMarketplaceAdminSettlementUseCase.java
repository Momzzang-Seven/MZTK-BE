package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ExecuteMarketplaceAdminSettlementCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionResult;

public interface ExecuteMarketplaceAdminSettlementUseCase {

  MarketplaceAdminExecutionResult execute(ExecuteMarketplaceAdminSettlementCommand command);
}
