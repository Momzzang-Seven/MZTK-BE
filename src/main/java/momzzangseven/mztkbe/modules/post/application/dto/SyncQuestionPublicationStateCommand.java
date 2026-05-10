package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;

public record SyncQuestionPublicationStateCommand(
    Long postId, String executionIntentId, String terminalStatus, String failureReason) {

  public void validateForConfirm() {
    if (postId == null) {
      throw new PostInvalidInputException("postId is required.");
    }
    if (executionIntentId == null || executionIntentId.isBlank()) {
      throw new PostInvalidInputException("executionIntentId is required.");
    }
  }

  public void validateForFailure() {
    validateForConfirm();
    if (terminalStatus == null || terminalStatus.isBlank()) {
      throw new PostInvalidInputException("terminalStatus is required.");
    }
  }
}
