package momzzangseven.mztkbe.modules.web3.admin.application.port.in;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminSettlementCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminSettlementResult;

public interface ForceMarketplaceAdminSettlementUseCase {

  ForceMarketplaceAdminSettlementResult execute(ForceMarketplaceAdminSettlementCommand command);
}
