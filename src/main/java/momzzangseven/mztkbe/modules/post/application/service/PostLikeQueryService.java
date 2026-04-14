package momzzangseven.mztkbe.modules.post.application.service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.in.GetAnswerLikeStatusUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostLikeQueryService implements GetAnswerLikeStatusUseCase {

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
