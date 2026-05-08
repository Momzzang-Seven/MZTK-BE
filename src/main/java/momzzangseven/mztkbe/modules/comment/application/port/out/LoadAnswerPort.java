package momzzangseven.mztkbe.modules.comment.application.port.out;

import java.util.Optional;

public interface LoadAnswerPort {

  Optional<AnswerCommentContext> loadAnswerCommentContext(Long answerId);

  Optional<AnswerCommentContext> loadAnswerCommentContextForUpdate(Long answerId);

  record AnswerCommentContext(Long answerId, Long postId, boolean answerLocked) {

    public AnswerCommentContext(Long answerId, Long postId) {
      this(answerId, postId, false);
    }
  }
}
