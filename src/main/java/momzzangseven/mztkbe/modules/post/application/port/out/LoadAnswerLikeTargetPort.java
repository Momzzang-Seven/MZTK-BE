package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.Optional;

public interface LoadAnswerLikeTargetPort {

  Optional<AnswerLikeTarget> loadAnswerTarget(Long answerId);

  record AnswerLikeTarget(Long answerId, Long postId) {}
}
