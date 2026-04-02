package momzzangseven.mztkbe.modules.post.infrastructure.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerSummaryUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadAnswerLikeTargetPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerLikeTargetAdapter implements LoadAnswerLikeTargetPort {

  private final GetAnswerSummaryUseCase getAnswerSummaryUseCase;

  @Override
  public Optional<AnswerLikeTarget> loadAnswerTarget(Long answerId) {
    return getAnswerSummaryUseCase
        .getAnswerSummary(answerId)
        .map(answer -> new AnswerLikeTarget(answer.answerId(), answer.postId()));
  }
}
