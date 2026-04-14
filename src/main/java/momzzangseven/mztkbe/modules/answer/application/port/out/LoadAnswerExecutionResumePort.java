package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Loads the latest answer execution resume summary when Web3 QnA wiring is enabled. */
public interface LoadAnswerExecutionResumePort {

  Optional<AnswerExecutionResumeView> loadLatest(Long answerId);

  default Map<Long, AnswerExecutionResumeView> loadLatestByAnswerIds(Collection<Long> answerIds) {
    Map<Long, AnswerExecutionResumeView> results = new LinkedHashMap<>();
    for (Long answerId : answerIds) {
      loadLatest(answerId).ifPresent(summary -> results.put(answerId, summary));
    }
    return results;
  }
}
