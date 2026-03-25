package momzzangseven.mztkbe.modules.post.infrastructure.external.answer;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.in.MarkAnswerAcceptedUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadAcceptedAnswerPort;
import momzzangseven.mztkbe.modules.post.application.port.out.MarkAcceptedAnswerPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AcceptedAnswerAdapter implements LoadAcceptedAnswerPort, MarkAcceptedAnswerPort {

  private final LoadAnswerPort loadAnswerPort;
  private final MarkAnswerAcceptedUseCase markAnswerAcceptedUseCase;

  @Override
  public Optional<AcceptedAnswerInfo> loadAcceptedAnswer(Long answerId) {
    return loadAnswerPort
        .loadAnswer(answerId)
        .map(
            answer ->
                new AcceptedAnswerInfo(answer.getId(), answer.getPostId(), answer.getUserId()));
  }

  @Override
  public void markAccepted(Long answerId) {
    markAnswerAcceptedUseCase.markAccepted(answerId);
  }
}
