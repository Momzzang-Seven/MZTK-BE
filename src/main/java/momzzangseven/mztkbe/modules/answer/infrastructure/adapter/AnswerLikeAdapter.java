package momzzangseven.mztkbe.modules.answer.infrastructure.adapter;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerLikePort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerLikeAdapter implements LoadAnswerLikePort {

  private final PostLikePersistencePort postLikePersistencePort;

  @Override
  public Map<Long, Long> countLikeByAnswerIds(Collection<Long> answerIds) {
    return postLikePersistencePort.countByTargetIds(PostLikeTargetType.ANSWER, answerIds);
  }

  @Override
  public Set<Long> loadLikedAnswerIds(Collection<Long> answerIds, Long userId) {
    return postLikePersistencePort.findLikedTargetIds(PostLikeTargetType.ANSWER, answerIds, userId);
  }
}
