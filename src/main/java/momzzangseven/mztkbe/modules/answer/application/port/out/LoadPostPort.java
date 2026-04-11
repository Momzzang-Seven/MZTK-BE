package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.Optional;

public interface LoadPostPort {

  Optional<PostContext> loadPost(Long postId);

  /** `isSolved` is kept for adapter compatibility and must be derived from post status. */
  record PostContext(Long postId, Long writerId, boolean isSolved, boolean questionPost) {}
}
