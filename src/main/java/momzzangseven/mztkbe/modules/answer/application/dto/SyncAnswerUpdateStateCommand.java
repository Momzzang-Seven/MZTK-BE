package momzzangseven.mztkbe.modules.answer.application.dto;

import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;

public record SyncAnswerUpdateStateCommand(
    Long answerId,
    Long updateVersion,
    String updateToken,
    String executionIntentId,
    String failureReason) {

  public void validateForConfirm() {
    if (answerId == null) {
      throw new AnswerInvalidInputException("answerId is required.");
    }
    if (updateVersion == null || updateVersion <= 0) {
      throw new AnswerInvalidInputException("updateVersion must be positive.");
    }
    if (updateToken == null || updateToken.isBlank()) {
      throw new AnswerInvalidInputException("updateToken is required.");
    }
    if (executionIntentId == null || executionIntentId.isBlank()) {
      throw new AnswerInvalidInputException("executionIntentId is required.");
    }
  }
}
