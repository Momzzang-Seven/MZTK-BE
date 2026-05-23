package momzzangseven.mztkbe.modules.web3.marketplace.application.port.out;

import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;

public interface BuildMarketplaceAdminExecutionDraftPort {

  MarketplaceExecutionDraft build(MarketplaceAdminEscrowExecutionRequest request);
}
