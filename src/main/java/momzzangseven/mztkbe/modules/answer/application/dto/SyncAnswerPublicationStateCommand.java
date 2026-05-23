package momzzangseven.mztkbe.modules.answer.application.dto;

import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;

public record SyncAnswerPublicationStateCommand(
    Long answerId, String executionIntentId, String terminalStatus, String failureReason) {

  public void validateForConfirm() {
    if (answerId == null) {
      throw new AnswerInvalidInputException("answerId is required.");
    }
    if (executionIntentId == null || executionIntentId.isBlank()) {
      throw new AnswerInvalidInputException("executionIntentId is required.");
    }
  }

  public void validateForFailure() {
    validateForConfirm();
    if (terminalStatus == null || terminalStatus.isBlank()) {
      throw new AnswerInvalidInputException("terminalStatus is required.");
    }
  }
}
