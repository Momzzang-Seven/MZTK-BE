package momzzangseven.mztkbe.modules.web3.admin.application.port.in;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminRefundReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminRefundReviewResult;

public interface GetQnaAdminRefundReviewUseCase {

  GetQnaAdminRefundReviewResult execute(GetQnaAdminRefundReviewQuery query);
}
