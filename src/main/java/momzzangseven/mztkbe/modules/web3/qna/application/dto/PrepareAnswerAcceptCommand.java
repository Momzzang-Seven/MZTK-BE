package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record PrepareAnswerAcceptCommand(
    Long postId,
    Long answerId,
    Long requesterUserId,
    Long answerWriterUserId,
    String questionContent,
    String answerContent,
    Long rewardMztk) {

  public void validate() {
    validatePositive(postId, "postId");
    validatePositive(answerId, "answerId");
    validatePositive(requesterUserId, "requesterUserId");
    validatePositive(answerWriterUserId, "answerWriterUserId");
    validateContent(questionContent, "questionContent");
    validateContent(answerContent, "answerContent");
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
