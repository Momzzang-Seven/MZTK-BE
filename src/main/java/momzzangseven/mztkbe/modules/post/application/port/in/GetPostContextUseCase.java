package momzzangseven.mztkbe.modules.post.application.port.in;

import java.util.Optional;

public interface GetPostContextUseCase {

  Optional<PostContext> getPostContext(Long postId);

  record PostContext(
      Long postId,
      Long writerId,
      boolean solved,
      boolean questionPost,
      String content,
      Long reward) {

    public PostContext(Long postId, Long writerId, boolean solved, boolean questionPost) {
      this(postId, writerId, solved, questionPost, null, null);
    }
  }
}
