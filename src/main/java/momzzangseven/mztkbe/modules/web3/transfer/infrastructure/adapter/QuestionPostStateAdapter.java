package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.in.MarkQuestionPostSolvedUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.MarkQuestionPostSolvedPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuestionPostStateAdapter implements MarkQuestionPostSolvedPort {

  private final MarkQuestionPostSolvedUseCase markQuestionPostSolvedUseCase;

  @Override
  public int markSolved(Long postId) {
    return markQuestionPostSolvedUseCase.execute(postId);
  }
}
