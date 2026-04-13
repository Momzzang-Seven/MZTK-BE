package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.dto.MarkQuestionPostSolvedCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.MarkQuestionPostSolvedUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarkQuestionPostSolvedService implements MarkQuestionPostSolvedUseCase {

  private final PostPersistencePort postPersistencePort;
  private final CountAnswersPort countAnswersPort;

  @Override
  @Transactional
  public int execute(MarkQuestionPostSolvedCommand command) {
    command.validate();
    long answerCount = countAnswersPort.countAnswers(command.postId());
    if (answerCount == 0) {
      return 0;
    }
    return postPersistencePort.markQuestionPostSolved(command.postId());
  }
}
