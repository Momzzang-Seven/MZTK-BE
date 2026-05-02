package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.post;

import momzzangseven.mztkbe.modules.post.application.dto.SyncQuestionPublicationStateCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.ConfirmQuestionCreatedUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.FailQuestionCreateUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionPublicationSyncPort;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnAnyExecutionEnabled
public class QnaQuestionPublicationSyncAdapter implements QnaQuestionPublicationSyncPort {

  private final ConfirmQuestionCreatedUseCase confirmQuestionCreatedUseCase;
  private final FailQuestionCreateUseCase failQuestionCreateUseCase;

  public QnaQuestionPublicationSyncAdapter(
      @Lazy ConfirmQuestionCreatedUseCase confirmQuestionCreatedUseCase,
      @Lazy FailQuestionCreateUseCase failQuestionCreateUseCase) {
    this.confirmQuestionCreatedUseCase = confirmQuestionCreatedUseCase;
    this.failQuestionCreateUseCase = failQuestionCreateUseCase;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void confirmQuestionCreated(Long postId, String executionIntentId) {
    confirmQuestionCreatedUseCase.confirmQuestionCreated(
        new SyncQuestionPublicationStateCommand(postId, executionIntentId, null, null));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failQuestionCreate(
      Long postId,
      String executionIntentId,
      ExecutionIntentStatus terminalStatus,
      String failureReason) {
    failQuestionCreateUseCase.failQuestionCreate(
        new SyncQuestionPublicationStateCommand(
            postId, executionIntentId, terminalStatus.name(), failureReason));
  }
}
