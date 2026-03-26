package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;

public record AcceptAnswerCommand(Long postId, Long answerId, Long requesterId) {

  public void validate() {
    if (postId == null || postId <= 0) {
      throw new PostInvalidInputException("postId must be positive.");
    }
    if (answerId == null || answerId <= 0) {
      throw new PostInvalidInputException("answerId must be positive.");
    }
    if (requesterId == null || requesterId <= 0) {
      throw new PostInvalidInputException("requesterId must be positive.");
    }
  }
}
