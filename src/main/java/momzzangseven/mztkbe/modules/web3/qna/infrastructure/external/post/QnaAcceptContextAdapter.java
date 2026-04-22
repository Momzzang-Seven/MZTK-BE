package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.post;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerSummaryForUpdateUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerSummaryUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostContextUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAcceptContextPort;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnQnaAutoAcceptEnabled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnQnaAutoAcceptEnabled
public class QnaAcceptContextAdapter implements LoadQnaAcceptContextPort {

  private final GetPostContextUseCase getPostContextUseCase;
  private final GetAnswerSummaryForUpdateUseCase getAnswerSummaryForUpdateUseCase;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<QnaAcceptContext> loadForUpdate(Long postId, Long answerId) {
    Optional<GetAnswerSummaryUseCase.AnswerSummary> answer =
        getAnswerSummaryForUpdateUseCase.getAnswerSummaryForUpdate(answerId);
    Optional<GetPostContextUseCase.PostContext> post =
        getPostContextUseCase.getPostContextForUpdate(postId);
    if (post.isEmpty() || answer.isEmpty()) {
      return Optional.empty();
    }
    if (!answer.orElseThrow().postId().equals(post.orElseThrow().postId())) {
      return Optional.empty();
    }
    return Optional.of(
        new QnaAcceptContext(
            post.orElseThrow().postId(),
            post.orElseThrow().writerId(),
            answer.orElseThrow().answerId(),
            answer.orElseThrow().userId(),
            post.orElseThrow().content(),
            answer.orElseThrow().content()));
  }
}
