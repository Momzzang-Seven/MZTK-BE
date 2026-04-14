package momzzangseven.mztkbe.modules.post.application.port.in;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface GetAnswerLikeStatusUseCase {

  Map<Long, Long> countLikeByAnswerIds(Collection<Long> answerIds);

  Set<Long> loadLikedAnswerIds(Collection<Long> answerIds, Long userId);
}
