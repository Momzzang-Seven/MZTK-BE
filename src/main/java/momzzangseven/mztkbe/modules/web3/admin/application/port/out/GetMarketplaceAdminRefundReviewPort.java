package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminRefundReviewResult;

public interface GetMarketplaceAdminRefundReviewPort {

  GetMarketplaceAdminRefundReviewResult getRefundReview(
      Long reservationId, boolean canManualRefund);
}
