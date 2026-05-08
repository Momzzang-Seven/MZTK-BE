package momzzangseven.mztkbe.modules.comment.infrastructure.external.answer.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerSummaryForUpdateUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerSummaryUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostContextUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentAnswerAdapter implements LoadAnswerPort {

  private final GetAnswerSummaryUseCase getAnswerSummaryUseCase;
  private final GetAnswerSummaryForUpdateUseCase getAnswerSummaryForUpdateUseCase;
  private final GetPostContextUseCase getPostContextUseCase;

  @Override
  public Optional<AnswerCommentContext> loadAnswerCommentContext(Long answerId) {
    return getAnswerSummaryUseCase
        .getAnswerSummary(answerId)
        .map(
            answer ->
                new AnswerCommentContext(
                    answer.answerId(), answer.postId(), loadAnswerLocked(answer.postId())));
  }

  @Override
  public Optional<AnswerCommentContext> loadAnswerCommentContextForUpdate(Long answerId) {
    return getAnswerSummaryForUpdateUseCase
        .getAnswerSummaryForUpdate(answerId)
        .map(
            answer ->
                new AnswerCommentContext(
                    answer.answerId(), answer.postId(), loadAnswerLocked(answer.postId())));
  }

  private boolean loadAnswerLocked(Long postId) {
    return getPostContextUseCase
        .getPostContext(postId)
        .map(GetPostContextUseCase.PostContext::answerLocked)
        .orElse(false);
  }
}
