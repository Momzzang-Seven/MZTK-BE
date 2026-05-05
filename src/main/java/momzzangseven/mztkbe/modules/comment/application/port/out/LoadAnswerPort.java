package momzzangseven.mztkbe.modules.comment.application.port.out;

import java.util.Optional;

public interface LoadAnswerPort {

  Optional<AnswerCommentContext> loadAnswerCommentContext(Long answerId);

  record AnswerCommentContext(Long answerId, Long postId) {}
}
