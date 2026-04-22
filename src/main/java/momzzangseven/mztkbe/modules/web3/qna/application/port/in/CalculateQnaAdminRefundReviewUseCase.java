package momzzangseven.mztkbe.modules.web3.qna.application.port.in;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.CalculateQnaAdminRefundReviewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminRefundReviewResult;

public interface CalculateQnaAdminRefundReviewUseCase {

  QnaAdminRefundReviewResult execute(CalculateQnaAdminRefundReviewQuery query);
}
