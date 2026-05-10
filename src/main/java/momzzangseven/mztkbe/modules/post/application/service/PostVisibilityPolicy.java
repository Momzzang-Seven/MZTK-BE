package momzzangseven.mztkbe.modules.post.application.service;

import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import org.springframework.stereotype.Component;

@Component
public class PostVisibilityPolicy {

  public void validateReadable(Post post, Long requesterUserId) {
    if (!canRead(post, requesterUserId)) {
      throw new PostNotFoundException();
    }
  }

  public boolean canRead(Post post, Long requesterUserId) {
    return post.isPubliclyVisible() || post.isOwnedBy(requesterUserId);
  }

  public void validateWritable(Post post) {
    if (!post.isPubliclyVisible()) {
      throw new PostInvalidInputException(
          "Post is not in a state that allows the requested interaction.");
    }
  }

  public void validateOwnerMutationAllowed(Post post) {
    if (post.isModerationBlocked()) {
      throw new PostInvalidInputException("Blocked posts cannot be updated or deleted.");
    }
  }
}
