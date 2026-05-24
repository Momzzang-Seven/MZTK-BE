package momzzangseven.mztkbe.modules.web3.admin.application.port.in;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminSettlementReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminSettlementReviewResult;

public interface GetMarketplaceAdminSettlementReviewUseCase {

  GetMarketplaceAdminSettlementReviewResult execute(GetMarketplaceAdminSettlementReviewQuery query);
}
