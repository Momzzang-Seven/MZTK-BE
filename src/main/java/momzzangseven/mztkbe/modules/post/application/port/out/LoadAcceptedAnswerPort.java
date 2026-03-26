package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.Optional;

public interface LoadAcceptedAnswerPort {

  Optional<AcceptedAnswerInfo> loadAcceptedAnswer(Long answerId);

  record AcceptedAnswerInfo(Long answerId, Long postId, Long userId) {}
}
