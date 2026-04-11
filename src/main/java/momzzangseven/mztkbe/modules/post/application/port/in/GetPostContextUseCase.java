package momzzangseven.mztkbe.modules.post.application.port.in;

import java.util.Optional;

public interface GetPostContextUseCase {

  Optional<PostContext> getPostContext(Long postId);

  /** `solved` is a legacy boolean view derived from the post status. */
  record PostContext(Long postId, Long writerId, boolean solved, boolean questionPost) {}
}
