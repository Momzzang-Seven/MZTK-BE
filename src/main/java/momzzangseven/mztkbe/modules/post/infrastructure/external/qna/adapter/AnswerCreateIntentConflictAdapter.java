package momzzangseven.mztkbe.modules.post.infrastructure.external.qna.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadAnswerCreateIntentConflictPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.CheckQnaAnswerCreateIntentConflictUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerCreateIntentConflictAdapter implements LoadAnswerCreateIntentConflictPort {

  private final CheckQnaAnswerCreateIntentConflictUseCase conflictUseCase;

  @Override
  public boolean hasActiveAnswerCreateIntent(Long postId) {
    return conflictUseCase.hasActiveAnswerCreateIntent(postId);
  }
}
