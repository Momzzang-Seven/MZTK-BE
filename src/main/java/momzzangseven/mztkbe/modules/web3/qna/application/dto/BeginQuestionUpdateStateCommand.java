package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record BeginQuestionUpdateStateCommand(
    Long postId, Long requesterUserId, String expectedQuestionHash) {

  public void validate() {
    validatePositive(postId, "postId");
    validatePositive(requesterUserId, "requesterUserId");
    if (expectedQuestionHash == null || expectedQuestionHash.isBlank()) {
      throw new Web3InvalidInputException("expectedQuestionHash is required");
    }
  }

  private static void validatePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new Web3InvalidInputException(fieldName + " must be positive");
    }
  }
}
