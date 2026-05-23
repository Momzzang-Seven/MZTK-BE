package momzzangseven.mztkbe.modules.post.infrastructure.external.answer.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetVisibleAnswerSummaryUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadAnswerLikeTargetPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerLikeTargetAdapter implements LoadAnswerLikeTargetPort {

  private final GetVisibleAnswerSummaryUseCase getVisibleAnswerSummaryUseCase;

  @Override
  public Optional<AnswerLikeTarget> loadAnswerTarget(Long answerId) {
    return getVisibleAnswerSummaryUseCase
        .getVisibleAnswerSummary(answerId)
        .map(answer -> new AnswerLikeTarget(answer.answerId(), answer.postId()));
  }
}
