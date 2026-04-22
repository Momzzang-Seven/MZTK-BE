package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record QnaAdminReviewValidationItem(
    String code, boolean valid, boolean warning, String detail) {

  public QnaAdminReviewValidationItem {
    if (code == null || code.isBlank()) {
      throw new Web3InvalidInputException("code is required");
    }
  }
}
