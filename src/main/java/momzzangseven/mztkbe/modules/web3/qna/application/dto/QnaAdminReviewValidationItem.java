package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record QnaAdminReviewValidationItem(
    QnaAdminReviewValidationCode code, boolean valid, boolean warning, String detail) {

  public QnaAdminReviewValidationItem {
    if (code == null) {
      throw new Web3InvalidInputException("code is required");
    }
  }
}
