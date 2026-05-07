package momzzangseven.mztkbe.modules.comment.infrastructure.external.answer.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerSummaryForUpdateUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerSummaryUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadAnswerPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentAnswerAdapter implements LoadAnswerPort {

  private final GetAnswerSummaryUseCase getAnswerSummaryUseCase;
  private final GetAnswerSummaryForUpdateUseCase getAnswerSummaryForUpdateUseCase;

  @Override
  public Optional<AnswerCommentContext> loadAnswerCommentContext(Long answerId) {
    return getAnswerSummaryUseCase
        .getAnswerSummary(answerId)
        .map(answer -> new AnswerCommentContext(answer.answerId(), answer.postId()));
  }

  @Override
  public Optional<AnswerCommentContext> loadAnswerCommentContextForUpdate(Long answerId) {
    return getAnswerSummaryForUpdateUseCase
        .getAnswerSummaryForUpdate(answerId)
        .map(answer -> new AnswerCommentContext(answer.answerId(), answer.postId()));
  }
}
