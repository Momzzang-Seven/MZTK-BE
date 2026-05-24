package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminSettlementReviewResult;

public interface GetMarketplaceAdminSettlementReviewPort {

  GetMarketplaceAdminSettlementReviewResult getSettlementReview(
      Long reservationId, boolean canEarlySettle);
}
