package momzzangseven.mztkbe.modules.answer.application.dto;

import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;

public record RecoverAnswerUpdateCommand(Long requesterId, Long postId, Long answerId) {

  public void validate() {
    if (requesterId == null) {
      throw new AnswerInvalidInputException("requesterId is required.");
    }
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    if (answerId == null) {
      throw new AnswerInvalidInputException("answerId is required.");
    }
  }
}
