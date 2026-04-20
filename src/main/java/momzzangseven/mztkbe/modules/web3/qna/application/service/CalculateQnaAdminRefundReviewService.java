package momzzangseven.mztkbe.modules.web3.qna.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.CalculateQnaAdminRefundReviewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminRefundReviewResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.CalculateQnaAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort;

@RequiredArgsConstructor
public class CalculateQnaAdminRefundReviewService implements CalculateQnaAdminRefundReviewUseCase {

  private final LoadQnaAdminReviewContextPort loadQnaAdminReviewContextPort;

  @Override
  public QnaAdminRefundReviewResult execute(CalculateQnaAdminRefundReviewQuery query) {
    query.validate();
    Long postId = query.postId();
    return QnaAdminReviewDecider.assessRefund(
        postId, loadQnaAdminReviewContextPort.loadRefund(postId));
  }
}
