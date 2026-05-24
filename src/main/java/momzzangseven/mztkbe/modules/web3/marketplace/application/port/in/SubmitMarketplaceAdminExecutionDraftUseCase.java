package momzzangseven.mztkbe.modules.web3.marketplace.application.port.in;

import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminExecutionDraftSubmitResult;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;

public interface SubmitMarketplaceAdminExecutionDraftUseCase {

  MarketplaceAdminExecutionDraftSubmitResult execute(MarketplaceExecutionDraft draft);
}
