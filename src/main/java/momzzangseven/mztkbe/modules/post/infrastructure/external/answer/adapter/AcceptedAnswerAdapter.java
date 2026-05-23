package momzzangseven.mztkbe.modules.post.infrastructure.external.answer.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetVisibleAnswerSummaryForUpdateUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetVisibleAnswerSummaryUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.MarkAnswerAcceptedUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadAcceptedAnswerPort;
import momzzangseven.mztkbe.modules.post.application.port.out.MarkAcceptedAnswerPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AcceptedAnswerAdapter implements LoadAcceptedAnswerPort, MarkAcceptedAnswerPort {

  private final GetVisibleAnswerSummaryUseCase getVisibleAnswerSummaryUseCase;
  private final GetVisibleAnswerSummaryForUpdateUseCase getVisibleAnswerSummaryForUpdateUseCase;
  private final MarkAnswerAcceptedUseCase markAnswerAcceptedUseCase;

  @Override
  public Optional<AcceptedAnswerInfo> loadAcceptedAnswer(Long answerId) {
    return getVisibleAnswerSummaryUseCase
        .getVisibleAnswerSummary(answerId)
        .map(
            answer ->
                new AcceptedAnswerInfo(
                    answer.answerId(), answer.postId(), answer.userId(), answer.content()));
  }

  @Override
  public Optional<AcceptedAnswerInfo> loadAcceptedAnswerForUpdate(Long answerId) {
    return getVisibleAnswerSummaryForUpdateUseCase
        .getVisibleAnswerSummaryForUpdate(answerId)
        .map(
            answer ->
                new AcceptedAnswerInfo(
                    answer.answerId(), answer.postId(), answer.userId(), answer.content()));
  }

  @Override
  public void markAccepted(Long answerId) {
    markAnswerAcceptedUseCase.markAccepted(answerId);
  }
}
