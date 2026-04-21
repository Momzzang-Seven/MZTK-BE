package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.post;

import momzzangseven.mztkbe.modules.post.application.port.in.SyncQuestionAdminRefundStateUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAdminRefundStateSyncPort;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnAnyExecutionEnabled
public class QnaAdminRefundStateSyncAdapter implements QnaAdminRefundStateSyncPort {

  private final SyncQuestionAdminRefundStateUseCase syncQuestionAdminRefundStateUseCase;

  public QnaAdminRefundStateSyncAdapter(
      SyncQuestionAdminRefundStateUseCase syncQuestionAdminRefundStateUseCase) {
    this.syncQuestionAdminRefundStateUseCase = syncQuestionAdminRefundStateUseCase;
  }

  @Override
  public void beginPendingRefund(Long postId) {
    syncQuestionAdminRefundStateUseCase.beginPendingRefund(postId);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void rollbackPendingRefund(Long postId) {
    syncQuestionAdminRefundStateUseCase.rollbackPendingRefund(postId);
  }
}
