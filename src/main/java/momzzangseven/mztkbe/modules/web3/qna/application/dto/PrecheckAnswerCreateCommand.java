package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Pre-validates whether a local answer can safely prepare an on-chain submit attempt. */
public record PrecheckAnswerCreateCommand(Long postId, String questionContent) {

  public void validate() {
    if (postId == null || postId <= 0) {
      throw new Web3InvalidInputException("postId must be positive");
    }
    if (questionContent == null || questionContent.isBlank()) {
      throw new Web3InvalidInputException("questionContent is required");
    }
  }
}
