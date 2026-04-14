package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Command for canceling QUESTION_REWARD intent when acceptance is reverted/changed. */
public record CancelQuestionRewardIntentCommand(Long postId, Long acceptedCommentId) {

  public void validate() {
    if (postId == null || postId <= 0) {
      throw new Web3InvalidInputException("postId must be positive");
    }
    if (acceptedCommentId != null && acceptedCommentId <= 0) {
      throw new Web3InvalidInputException("acceptedCommentId must be positive when provided");
    }
  }
}
