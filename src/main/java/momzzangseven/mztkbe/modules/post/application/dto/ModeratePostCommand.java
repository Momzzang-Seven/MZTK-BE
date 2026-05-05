package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;

public record ModeratePostCommand(Long operatorId, Long postId) {

  public void validate() {
    if (operatorId == null || operatorId <= 0) {
      throw new PostInvalidInputException("operatorId must be positive.");
    }
    if (postId == null || postId <= 0) {
      throw new PostInvalidInputException("postId must be positive.");
    }
  }
}
