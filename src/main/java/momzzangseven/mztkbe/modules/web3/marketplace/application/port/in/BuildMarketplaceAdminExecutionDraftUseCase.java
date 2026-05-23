package momzzangseven.mztkbe.modules.web3.marketplace.application.port.in;

import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;

public interface BuildMarketplaceAdminExecutionDraftUseCase {

  MarketplaceExecutionDraft execute(MarketplaceAdminEscrowExecutionRequest request);
}
