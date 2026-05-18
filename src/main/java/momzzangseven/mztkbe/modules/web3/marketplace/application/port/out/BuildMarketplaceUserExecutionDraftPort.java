package momzzangseven.mztkbe.modules.web3.marketplace.application.port.out;

import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;

/** Output port for building a marketplace execution draft from a reservation-owned snapshot. */
public interface BuildMarketplaceUserExecutionDraftPort {

  MarketplaceExecutionDraft build(MarketplaceEscrowExecutionRequest request);
}
