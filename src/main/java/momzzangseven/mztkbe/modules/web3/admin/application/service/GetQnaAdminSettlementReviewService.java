package momzzangseven.mztkbe.modules.web3.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminSettlementReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminSettlementReviewResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetQnaAdminSettlementReviewUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.GetQnaAdminSettlementReviewPort;

@RequiredArgsConstructor
public class GetQnaAdminSettlementReviewService implements GetQnaAdminSettlementReviewUseCase {

  private final GetQnaAdminSettlementReviewPort getQnaAdminSettlementReviewPort;

  @Override
  public GetQnaAdminSettlementReviewResult execute(GetQnaAdminSettlementReviewQuery query) {
    if (query == null) {
      throw new Web3InvalidInputException("query is required");
    }
    query.validate();
    return getQnaAdminSettlementReviewPort.getSettlementReview(query.postId(), query.answerId());
  }
}
