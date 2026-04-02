package momzzangseven.mztkbe.modules.answer.application.port.in;

import java.util.Optional;

public interface GetAnswerSummaryUseCase {

  Optional<AnswerSummary> getAnswerSummary(Long answerId);

  record AnswerSummary(Long answerId, Long postId, Long userId) {}
}
