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
      Long reward,
      boolean answerLocked) {

    public PostContext(Long postId, Long writerId, boolean isSolved, boolean questionPost) {
      this(postId, writerId, isSolved, questionPost, null, null, isSolved);
    }

    public PostContext(
        Long postId,
        Long writerId,
        boolean isSolved,
        boolean questionPost,
        String content,
        Long reward) {
      this(postId, writerId, isSolved, questionPost, content, reward, isSolved);
    }
  }
}
