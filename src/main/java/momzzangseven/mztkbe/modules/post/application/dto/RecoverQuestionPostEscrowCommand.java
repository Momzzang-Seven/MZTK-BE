package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;

/** Command for recreating a missing question-create escrow intent from local question state. */
public record RecoverQuestionPostEscrowCommand(Long requesterId, Long postId) {

  public void validate() {
    if (requesterId == null || requesterId <= 0) {
      throw new PostInvalidInputException("requesterId must be positive.");
    }
    if (postId == null || postId <= 0) {
      throw new PostInvalidInputException("postId must be positive.");
    }
  }
}
