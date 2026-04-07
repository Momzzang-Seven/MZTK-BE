package momzzangseven.mztkbe.modules.answer.infrastructure.adapter;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerLikePort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostLikePort;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerLikeAdapter implements LoadAnswerLikePort {

  private final LoadPostLikePort loadPostLikePort;

  @Override
  public Map<Long, Long> countLikeByAnswerIds(Collection<Long> answerIds) {
    return loadPostLikePort.countByTargetIds(PostLikeTargetType.ANSWER, answerIds);
  }

  @Override
  public Set<Long> loadLikedAnswerIds(Collection<Long> answerIds, Long userId) {
    return loadPostLikePort.findLikedTargetIds(PostLikeTargetType.ANSWER, answerIds, userId);
  }
}
