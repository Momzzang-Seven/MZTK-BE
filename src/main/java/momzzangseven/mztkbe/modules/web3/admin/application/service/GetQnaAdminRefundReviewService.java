package momzzangseven.mztkbe.modules.web3.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminRefundReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminRefundReviewResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetQnaAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.GetQnaAdminRefundReviewPort;

@RequiredArgsConstructor
public class GetQnaAdminRefundReviewService implements GetQnaAdminRefundReviewUseCase {

  private final GetQnaAdminRefundReviewPort getQnaAdminRefundReviewPort;

  @Override
  public GetQnaAdminRefundReviewResult execute(GetQnaAdminRefundReviewQuery query) {
    if (query == null) {
      throw new Web3InvalidInputException("query is required");
    }
    query.validate();
    return getQnaAdminRefundReviewPort.getRefundReview(query.postId());
  }
}
