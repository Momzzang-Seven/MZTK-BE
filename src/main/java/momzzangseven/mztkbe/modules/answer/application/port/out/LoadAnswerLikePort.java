package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface LoadAnswerLikePort {

  Map<Long, Long> countLikeByAnswerIds(Collection<Long> answerIds);

  Set<Long> loadLikedAnswerIds(Collection<Long> answerIds, Long userId);
}
