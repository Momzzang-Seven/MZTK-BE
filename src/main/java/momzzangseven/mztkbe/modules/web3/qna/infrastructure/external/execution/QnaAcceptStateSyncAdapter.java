package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import momzzangseven.mztkbe.modules.post.application.port.in.SyncAcceptedAnswerUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAcceptStateSyncPort;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnAnyExecutionEnabled
public class QnaAcceptStateSyncAdapter implements QnaAcceptStateSyncPort {

  private final SyncAcceptedAnswerUseCase syncAcceptedAnswerUseCase;

  public QnaAcceptStateSyncAdapter(@Lazy SyncAcceptedAnswerUseCase syncAcceptedAnswerUseCase) {
    this.syncAcceptedAnswerUseCase = syncAcceptedAnswerUseCase;
  }

  @Override
  public void beginPendingAccept(Long postId, Long answerId) {
    syncAcceptedAnswerUseCase.beginPendingAccept(postId, answerId);
  }

  @Override
  public void confirmAccepted(Long postId, Long answerId) {
    syncAcceptedAnswerUseCase.confirmAccepted(postId, answerId);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void rollbackPendingAccept(Long postId, Long answerId) {
    syncAcceptedAnswerUseCase.rollbackPendingAccept(postId, answerId);
  }
}
