package momzzangseven.mztkbe.modules.post.infrastructure.external.answer;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadAcceptedAnswerPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AcceptedAnswerAdapter implements LoadAcceptedAnswerPort {

  private final LoadAnswerPort loadAnswerPort;

  @Override
  public Optional<AcceptedAnswerInfo> loadAcceptedAnswer(Long answerId) {
    return loadAnswerPort
        .loadAnswer(answerId)
        .map(
            answer ->
                new AcceptedAnswerInfo(answer.getId(), answer.getPostId(), answer.getUserId()));
  }
}
