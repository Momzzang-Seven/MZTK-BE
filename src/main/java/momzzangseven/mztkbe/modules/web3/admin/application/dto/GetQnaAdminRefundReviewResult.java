package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminRefundReviewResult;

public record GetQnaAdminRefundReviewResult(QnaAdminRefundReviewResult review) {

  public GetQnaAdminRefundReviewResult {
    if (review == null) {
      throw new Web3InvalidInputException("review is required");
    }
  }
}
