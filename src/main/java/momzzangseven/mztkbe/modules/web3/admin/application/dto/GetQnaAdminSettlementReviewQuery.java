package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record GetQnaAdminSettlementReviewQuery(Long postId, Long answerId) {

  public void validate() {
    validatePositive(postId, "postId");
    validatePositive(answerId, "answerId");
  }

  private static void validatePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new Web3InvalidInputException(fieldName + " must be positive");
    }
  }
}
