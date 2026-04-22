package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminRefundReviewResult;

public interface GetQnaAdminRefundReviewPort {

  GetQnaAdminRefundReviewResult getRefundReview(Long postId);
}
