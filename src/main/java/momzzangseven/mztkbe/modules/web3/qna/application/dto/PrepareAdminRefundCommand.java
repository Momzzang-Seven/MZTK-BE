package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record PrepareAdminRefundCommand(Long postId, Long requesterUserId) {

  public void validate() {
    validatePositive(postId, "postId");
    validatePositive(requesterUserId, "requesterUserId");
  }

  private static void validatePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new Web3InvalidInputException(fieldName + " must be positive");
    }
  }
}
