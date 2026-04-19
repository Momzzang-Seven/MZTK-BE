package momzzangseven.mztkbe.modules.post.application.port.in;

import java.util.Optional;

public interface GetPostContextUseCase {

  Optional<PostContext> getPostContext(Long postId);

  Optional<PostContext> getPostContextForUpdate(Long postId);

  record PostContext(
      Long postId,
      Long writerId,
      boolean solved,
      boolean questionPost,
      String content,
      Long reward,
      boolean answerLocked) {

    public PostContext(Long postId, Long writerId, boolean solved, boolean questionPost) {
      this(postId, writerId, solved, questionPost, null, null, solved);
    }

    public PostContext(
        Long postId,
        Long writerId,
        boolean solved,
        boolean questionPost,
        String content,
        Long reward) {
      this(postId, writerId, solved, questionPost, content, reward, solved);
    }
  }
}
