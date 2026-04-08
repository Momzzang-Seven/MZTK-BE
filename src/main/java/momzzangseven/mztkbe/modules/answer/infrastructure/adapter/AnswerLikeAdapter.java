package momzzangseven.mztkbe.modules.answer.infrastructure.adapter;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerLikePort;
import momzzangseven.mztkbe.modules.post.application.port.in.GetAnswerLikeStatusUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerLikeAdapter implements LoadAnswerLikePort {

  private final GetAnswerLikeStatusUseCase getAnswerLikeStatusUseCase;

  @Override
  public Map<Long, Long> countLikeByAnswerIds(Collection<Long> answerIds) {
    return getAnswerLikeStatusUseCase.countLikeByAnswerIds(answerIds);
  }

  @Override
  public Set<Long> loadLikedAnswerIds(Collection<Long> answerIds, Long userId) {
    return getAnswerLikeStatusUseCase.loadLikedAnswerIds(answerIds, userId);
  }
}
