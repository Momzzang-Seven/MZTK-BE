package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record PrecheckQuestionCreateCommand(Long requesterUserId, Long rewardMztk) {

  public void validate() {
    if (requesterUserId == null || requesterUserId <= 0) {
      throw new Web3InvalidInputException("requesterUserId must be positive");
    }
    if (rewardMztk == null || rewardMztk <= 0) {
      throw new Web3InvalidInputException("rewardMztk must be positive");
    }
  }
}
