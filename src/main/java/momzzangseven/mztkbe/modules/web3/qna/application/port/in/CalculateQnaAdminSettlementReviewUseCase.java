package momzzangseven.mztkbe.modules.web3.qna.application.port.in;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.CalculateQnaAdminSettlementReviewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminSettlementReviewResult;

public interface CalculateQnaAdminSettlementReviewUseCase {

  QnaAdminSettlementReviewResult execute(CalculateQnaAdminSettlementReviewQuery query);
}
