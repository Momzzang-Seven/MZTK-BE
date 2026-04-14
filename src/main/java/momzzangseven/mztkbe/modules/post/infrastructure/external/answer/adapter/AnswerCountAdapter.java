package momzzangseven.mztkbe.modules.post.infrastructure.external.answer.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.in.CountAnswersUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.CountAnswersPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerCountAdapter implements CountAnswersPort {

  private final CountAnswersUseCase countAnswersUseCase;

  @Override
  public long countAnswers(Long postId) {
    return countAnswersUseCase.countAnswers(postId);
  }
}
