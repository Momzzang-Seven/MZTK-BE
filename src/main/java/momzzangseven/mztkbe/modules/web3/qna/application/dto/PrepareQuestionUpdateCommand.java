package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record PrepareQuestionUpdateCommand(
    Long postId,
    Long requesterUserId,
    String questionContent,
    Long rewardMztk,
    Long questionUpdateVersion,
    String questionUpdateToken) {

  public PrepareQuestionUpdateCommand(
      Long postId, Long requesterUserId, String questionContent, Long rewardMztk) {
    this(postId, requesterUserId, questionContent, rewardMztk, null, null);
  }

  public void validate() {
    validatePositive(postId, "postId");
    validatePositive(requesterUserId, "requesterUserId");
    validateContent(questionContent, "questionContent");
    validatePositive(rewardMztk, "rewardMztk");
  }

  public void validateVersionTokenRequired() {
    validate();
    validatePositive(questionUpdateVersion, "questionUpdateVersion");
    if (questionUpdateToken == null || questionUpdateToken.isBlank()) {
      throw new Web3InvalidInputException("questionUpdateToken is required");
    }
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
