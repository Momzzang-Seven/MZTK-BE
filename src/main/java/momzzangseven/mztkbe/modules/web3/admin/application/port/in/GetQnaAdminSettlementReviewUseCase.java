package momzzangseven.mztkbe.modules.web3.admin.application.port.in;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminSettlementReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminSettlementReviewResult;

public interface GetQnaAdminSettlementReviewUseCase {

  GetQnaAdminSettlementReviewResult execute(GetQnaAdminSettlementReviewQuery query);
}
