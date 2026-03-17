package momzzangseven.mztkbe.modules.answer.application.dto;

import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;

public record DeleteAnswerCommand(Long postId, Long answerId, Long userId) {

  public DeleteAnswerCommand {
    validate(postId, answerId, userId);
  }

  public void validate() {
    validate(postId, answerId, userId);
  }

  private static void validate(Long postId, Long answerId, Long userId) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    if (answerId == null) {
      throw new AnswerInvalidInputException("answerId is required.");
    }
    if (userId == null) {
      throw new AnswerInvalidInputException("userId is required.");
    }
  }
}
