package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record PrepareAnswerUpdateCommand(
    Long postId,
    Long answerId,
    Long requesterUserId,
    Long questionWriterUserId,
    String questionContent,
    Long rewardMztk,
    String answerContent,
    int activeAnswerCount,
    Long updateVersion,
    String updateToken) {

  public PrepareAnswerUpdateCommand(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long questionWriterUserId,
      String questionContent,
      Long rewardMztk,
      String answerContent,
      int activeAnswerCount) {
    this(
        postId,
        answerId,
        requesterUserId,
        questionWriterUserId,
        questionContent,
        rewardMztk,
        answerContent,
        activeAnswerCount,
        null,
        null);
  }

  public void validate() {
    validatePositive(postId, "postId");
    validatePositive(answerId, "answerId");
    validatePositive(requesterUserId, "requesterUserId");
    validatePositive(questionWriterUserId, "questionWriterUserId");
    validatePositive(rewardMztk, "rewardMztk");
    validateContent(questionContent, "questionContent");
    validateContent(answerContent, "answerContent");
    if (activeAnswerCount <= 0) {
      throw new Web3InvalidInputException("activeAnswerCount must be positive");
    }
    boolean hasUpdateVersion = updateVersion != null;
    boolean hasUpdateToken = updateToken != null && !updateToken.isBlank();
    if (hasUpdateVersion != hasUpdateToken) {
      throw new Web3InvalidInputException(
          "updateVersion and updateToken must be provided together");
    }
    if (hasUpdateVersion && updateVersion <= 0) {
      throw new Web3InvalidInputException("updateVersion must be positive");
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
