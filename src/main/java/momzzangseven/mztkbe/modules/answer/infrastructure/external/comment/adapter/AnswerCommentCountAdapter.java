package momzzangseven.mztkbe.modules.answer.infrastructure.external.comment.adapter;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.CountAnswerCommentsPort;
import momzzangseven.mztkbe.modules.comment.application.port.in.CountCommentsUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerCommentCountAdapter implements CountAnswerCommentsPort {

  private final CountCommentsUseCase countCommentsUseCase;

  @Override
  public Map<Long, Long> countCommentsByAnswerIds(List<Long> answerIds) {
    return countCommentsUseCase.countCommentsByAnswerIds(answerIds);
  }
}
