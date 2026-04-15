package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import momzzangseven.mztkbe.modules.post.application.port.in.SyncAcceptedAnswerUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAcceptStateSyncPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class QnaAcceptStateSyncAdapter implements QnaAcceptStateSyncPort {

  private final SyncAcceptedAnswerUseCase syncAcceptedAnswerUseCase;

  public QnaAcceptStateSyncAdapter(@Lazy SyncAcceptedAnswerUseCase syncAcceptedAnswerUseCase) {
    this.syncAcceptedAnswerUseCase = syncAcceptedAnswerUseCase;
  }

  @Override
  public void confirmAccepted(Long postId, Long answerId) {
    syncAcceptedAnswerUseCase.confirmAccepted(postId, answerId);
  }

  @Override
  public void rollbackPendingAccept(Long postId, Long answerId) {
    syncAcceptedAnswerUseCase.rollbackPendingAccept(postId, answerId);
  }
}
