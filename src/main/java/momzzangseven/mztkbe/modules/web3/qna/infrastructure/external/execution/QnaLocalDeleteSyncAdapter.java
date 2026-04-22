package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.in.ConfirmAnswerDeleteSyncUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.ConfirmQuestionDeleteSyncUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaLocalDeleteSyncPort;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import org.springframework.stereotype.Component;

/**
 * Bridges confirmed QnA escrow delete outcomes back into local question/answer hard deletes.
 *
 * <p>The adapter is intentionally idempotent: missing local rows are ignored because a previous
 * sync attempt may already have completed.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnAnyExecutionEnabled
public class QnaLocalDeleteSyncAdapter implements QnaLocalDeleteSyncPort {

  private final ConfirmQuestionDeleteSyncUseCase confirmQuestionDeleteSyncUseCase;
  private final ConfirmAnswerDeleteSyncUseCase confirmAnswerDeleteSyncUseCase;

  @Override
  public void confirmQuestionDeleted(Long postId) {
    confirmQuestionDeleteSyncUseCase.confirmDeleted(postId);
  }

  @Override
  public void confirmAnswerDeleted(Long answerId) {
    confirmAnswerDeleteSyncUseCase.confirmDeleted(answerId);
  }
}
