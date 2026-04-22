package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminSettlementReviewResult;

public interface GetQnaAdminSettlementReviewPort {

  GetQnaAdminSettlementReviewResult getSettlementReview(Long postId, Long answerId);
}
