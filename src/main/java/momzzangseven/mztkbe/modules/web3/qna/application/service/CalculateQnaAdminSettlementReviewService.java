package momzzangseven.mztkbe.modules.web3.qna.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.CalculateQnaAdminSettlementReviewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminSettlementReviewResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.CalculateQnaAdminSettlementReviewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort;

@RequiredArgsConstructor
public class CalculateQnaAdminSettlementReviewService
    implements CalculateQnaAdminSettlementReviewUseCase {

  private final LoadQnaAdminReviewContextPort loadQnaAdminReviewContextPort;

  @Override
  public QnaAdminSettlementReviewResult execute(CalculateQnaAdminSettlementReviewQuery query) {
    query.validate();
    Long postId = query.postId();
    Long answerId = query.answerId();
    return QnaAdminReviewDecider.assessSettlement(
        postId, answerId, loadQnaAdminReviewContextPort.loadSettlement(postId, answerId));
  }
}
