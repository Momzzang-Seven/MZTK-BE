package momzzangseven.mztkbe.modules.answer.application.dto;

import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;

/** Command for recreating a missing answer-submit escrow intent from local answer state. */
public record RecoverAnswerEscrowCommand(Long requesterId, Long postId, Long answerId) {

  public void validate() {
    if (requesterId == null || requesterId <= 0) {
      throw new AnswerInvalidInputException("userId is required");
    }
    if (postId == null || postId <= 0) {
      throw new AnswerInvalidInputException("postId is required");
    }
    if (answerId == null || answerId <= 0) {
      throw new AnswerInvalidInputException("answerId is required");
    }
  }
}
