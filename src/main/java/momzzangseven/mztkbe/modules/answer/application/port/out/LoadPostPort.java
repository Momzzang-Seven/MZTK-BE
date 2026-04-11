package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.Optional;

public interface LoadPostPort {

  Optional<PostContext> loadPost(Long postId);

  record PostContext(
      Long postId,
      Long writerId,
      boolean isSolved,
      boolean questionPost,
      String content,
      Long reward) {

    public PostContext(Long postId, Long writerId, boolean isSolved, boolean questionPost) {
      this(postId, writerId, isSolved, questionPost, null, null);
    }
  }
}
