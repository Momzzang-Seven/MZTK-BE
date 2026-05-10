package momzzangseven.mztkbe.modules.comment.application.port.out;

import java.util.Optional;

public interface LoadPostPort {

  Optional<PostVisibilityContext> loadPostVisibilityContext(Long postId);

  record PostVisibilityContext(Long postId, Long writerId, boolean publiclyVisible) {

    public boolean readableBy(Long requesterUserId) {
      return publiclyVisible
          || (requesterUserId != null && writerId != null && writerId.equals(requesterUserId));
    }

    public boolean writable() {
      return publiclyVisible;
    }
  }
}
