package momzzangseven.mztkbe.modules.web3.admin.application.port.in;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminRefundReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminRefundReviewResult;

public interface GetMarketplaceAdminRefundReviewUseCase {

  GetMarketplaceAdminRefundReviewResult execute(GetMarketplaceAdminRefundReviewQuery query);
}
