package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record MatchQuestionCreatePayloadCommand(
    Long postId, String questionContent, Long rewardMztk, QnaEscrowExecutionPayload payload) {

  public void validate() {
    if (postId == null || postId <= 0) {
      throw new Web3InvalidInputException("postId must be positive");
    }
    if (questionContent == null || questionContent.isBlank()) {
      throw new Web3InvalidInputException("questionContent is required");
    }
    if (rewardMztk == null || rewardMztk <= 0) {
      throw new Web3InvalidInputException("rewardMztk must be positive");
    }
    if (payload == null) {
      throw new Web3InvalidInputException("payload is required");
    }
  }
}
