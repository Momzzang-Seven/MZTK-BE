package momzzangseven.mztkbe.modules.answer.infrastructure.external.web3;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerExecutionResumeView;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerExecutionResumePort;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.GetQnaExecutionResumeBatchViewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.GetQnaExecutionResumeViewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionResumeViewResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.GetQnaExecutionResumeViewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnUserExecutionEnabled;
import org.springframework.stereotype.Component;

/**
 * Cross-module adapter that maps shared QnA execution resume reads into an answer-owned view.
 *
 * <p>Active only when the shared resume use case is wired; otherwise the answer module falls back
 * to {@link
 * momzzangseven.mztkbe.modules.answer.infrastructure.config.AnswerExecutionResumeStubConfig}.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnUserExecutionEnabled
public class AnswerExecutionResumeAdapter implements LoadAnswerExecutionResumePort {

  private final GetQnaExecutionResumeViewUseCase getQnaExecutionResumeViewUseCase;

  @Override
  public java.util.Optional<AnswerExecutionResumeView> loadLatest(Long answerId) {
    // Answer list exposes execution summary only to the owner row, never the full sign payload.
    return getQnaExecutionResumeViewUseCase
        .execute(new GetQnaExecutionResumeViewQuery(QnaExecutionResourceType.ANSWER, answerId))
        .map(this::toView);
  }

  @Override
  public Map<Long, AnswerExecutionResumeView> loadLatestByAnswerIds(Collection<Long> answerIds) {
    if (answerIds == null || answerIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, AnswerExecutionResumeView> results = new LinkedHashMap<>();
    getQnaExecutionResumeViewUseCase
        .executeBatch(
            new GetQnaExecutionResumeBatchViewQuery(
                QnaExecutionResourceType.ANSWER, answerIds.stream().distinct().toList()))
        .forEach((answerId, result) -> results.put(answerId, toView(result)));
    return results;
  }

  private AnswerExecutionResumeView toView(QnaExecutionResumeViewResult result) {
    return new AnswerExecutionResumeView(
        new AnswerExecutionResumeView.Resource(
            result.resource().type().name(),
            result.resource().id(),
            result.resource().status().name()),
        result.actionType(),
        new AnswerExecutionResumeView.ExecutionIntent(
            result.executionIntent().id(),
            result.executionIntent().status(),
            result.executionIntent().expiresAt()),
        new AnswerExecutionResumeView.Execution(
            result.execution().mode(), result.execution().signCount()),
        result.transaction() == null
            ? null
            : new AnswerExecutionResumeView.Transaction(
                result.transaction().id(),
                result.transaction().status(),
                result.transaction().txHash()));
  }
}
