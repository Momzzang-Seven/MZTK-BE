package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;

public record ModeratePostCommand(Long postId) {

  public void validate() {
    if (postId == null || postId <= 0) {
      throw new PostInvalidInputException("postId must be positive.");
    }
  }
}
