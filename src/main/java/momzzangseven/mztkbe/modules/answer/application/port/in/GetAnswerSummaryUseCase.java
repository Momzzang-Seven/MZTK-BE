package momzzangseven.mztkbe.modules.answer.application.port.in;

import java.util.Optional;

public interface GetAnswerSummaryUseCase {

  Optional<AnswerSummary> getAnswerSummary(Long answerId);

  record AnswerSummary(Long answerId, Long postId, Long userId, String content, boolean accepted) {

    public AnswerSummary(Long answerId, Long postId, Long userId) {
      this(answerId, postId, userId, null, false);
    }

    public AnswerSummary(Long answerId, Long postId, Long userId, String content) {
      this(answerId, postId, userId, content, false);
    }
  }
}
