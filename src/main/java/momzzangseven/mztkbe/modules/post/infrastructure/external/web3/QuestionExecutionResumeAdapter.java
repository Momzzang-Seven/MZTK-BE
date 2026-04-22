package momzzangseven.mztkbe.modules.post.infrastructure.external.web3;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadQuestionExecutionResumePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionResumeView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.GetQnaExecutionResumeViewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionResumeViewResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.GetQnaExecutionResumeViewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnUserExecutionEnabled;
import org.springframework.stereotype.Component;

/**
 * Cross-module adapter that maps shared QnA execution resume reads into a post-owned view.
 *
 * <p>Active only when the shared resume use case is wired; otherwise the post module falls back to
 * {@link
 * momzzangseven.mztkbe.modules.post.infrastructure.config.QuestionExecutionResumeStubConfig}.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnUserExecutionEnabled
public class QuestionExecutionResumeAdapter implements LoadQuestionExecutionResumePort {

  private final GetQnaExecutionResumeViewUseCase getQnaExecutionResumeViewUseCase;

  @Override
  public java.util.Optional<QuestionExecutionResumeView> loadLatest(Long postId) {
    // Public post detail only needs resumable state, not owner-bound sign request material.
    return getQnaExecutionResumeViewUseCase
        .execute(new GetQnaExecutionResumeViewQuery(QnaExecutionResourceType.QUESTION, postId))
        .map(this::toView);
  }

  private QuestionExecutionResumeView toView(QnaExecutionResumeViewResult result) {
    return new QuestionExecutionResumeView(
        new QuestionExecutionResumeView.Resource(
            result.resource().type().name(),
            result.resource().id(),
            result.resource().status().name()),
        result.actionType(),
        new QuestionExecutionResumeView.ExecutionIntent(
            result.executionIntent().id(),
            result.executionIntent().status(),
            result.executionIntent().expiresAt()),
        new QuestionExecutionResumeView.Execution(
            result.execution().mode(), result.execution().signCount()),
        result.transaction() == null
            ? null
            : new QuestionExecutionResumeView.Transaction(
                result.transaction().id(),
                result.transaction().status(),
                result.transaction().txHash()));
  }
}
