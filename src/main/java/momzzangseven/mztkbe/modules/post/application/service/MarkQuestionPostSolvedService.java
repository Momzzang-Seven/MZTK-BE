package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.in.MarkQuestionPostSolvedUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarkQuestionPostSolvedService implements MarkQuestionPostSolvedUseCase {

  private final PostPersistencePort postPersistencePort;

  @Override
  @Transactional
  public int execute(Long postId) {
    // Web3 success sync keeps the existing conditional bulk update path for idempotent no-op.
    return postPersistencePort.markQuestionPostSolved(postId);
  }
}
