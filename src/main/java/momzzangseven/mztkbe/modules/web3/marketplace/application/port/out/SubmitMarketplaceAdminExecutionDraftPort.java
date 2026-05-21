package momzzangseven.mztkbe.modules.web3.marketplace.application.port.out;

import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminExecutionDraftSubmitResult;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;

public interface SubmitMarketplaceAdminExecutionDraftPort {

  MarketplaceAdminExecutionDraftSubmitResult submit(MarketplaceExecutionDraft draft);
}
