package momzzangseven.mztkbe.modules.web3.marketplace.application.port.out;

import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionIntentResult;

/** Output port that submits a marketplace draft through the shared execution boundary. */
public interface SubmitMarketplaceExecutionDraftPort {

  MarketplaceExecutionIntentResult submit(MarketplaceExecutionDraft draft);
}
