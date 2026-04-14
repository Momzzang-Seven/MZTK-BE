package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record PrepareQuestionDeleteCommand(
    Long postId, Long requesterUserId, String questionContent, Long rewardMztk) {

  public void validate() {
    validatePositive(postId, "postId");
    validatePositive(requesterUserId, "requesterUserId");
    validateContent(questionContent, "questionContent");
    validatePositive(rewardMztk, "rewardMztk");
  }

  private static void validatePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new Web3InvalidInputException(fieldName + " must be positive");
    }
  }

  private static void validateContent(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new Web3InvalidInputException(fieldName + " must not be blank");
    }
  }
}
