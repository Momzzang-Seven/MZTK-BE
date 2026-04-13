package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.Optional;

public interface LoadAcceptedAnswerPort {

  Optional<AcceptedAnswerInfo> loadAcceptedAnswer(Long answerId);

  record AcceptedAnswerInfo(Long answerId, Long postId, Long userId, String content) {

    public AcceptedAnswerInfo(Long answerId, Long postId, Long userId) {
      this(answerId, postId, userId, null);
    }
  }
}
