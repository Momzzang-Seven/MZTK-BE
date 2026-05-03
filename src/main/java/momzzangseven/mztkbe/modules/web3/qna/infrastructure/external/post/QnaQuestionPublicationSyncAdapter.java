package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.post;

import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.post.application.dto.SyncQuestionPublicationStateCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.ConfirmQuestionCreatedUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.FailQuestionCreateUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionPublicationSyncPort;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@ConditionalOnAnyExecutionEnabled
public class QnaQuestionPublicationSyncAdapter implements QnaQuestionPublicationSyncPort {

  private final ApplicationEventPublisher eventPublisher;
  private final ConfirmQuestionCreatedUseCase confirmQuestionCreatedUseCase;
  private final FailQuestionCreateUseCase failQuestionCreateUseCase;

  public QnaQuestionPublicationSyncAdapter(
      ApplicationEventPublisher eventPublisher,
      @Lazy ConfirmQuestionCreatedUseCase confirmQuestionCreatedUseCase,
      @Lazy FailQuestionCreateUseCase failQuestionCreateUseCase) {
    this.eventPublisher = eventPublisher;
    this.confirmQuestionCreatedUseCase = confirmQuestionCreatedUseCase;
    this.failQuestionCreateUseCase = failQuestionCreateUseCase;
  }

  @Override
  public void confirmQuestionCreated(Long postId, String executionIntentId) {
    eventPublisher.publishEvent(
        QnaQuestionPublicationSyncEvent.confirmed(postId, executionIntentId));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleConfirmed(QnaQuestionPublicationSyncEvent.Confirmed event) {
    try {
      confirmQuestionCreatedUseCase.confirmQuestionCreated(
          new SyncQuestionPublicationStateCommand(
              event.postId(), event.executionIntentId(), null, null));
    } catch (Exception e) {
      log.error(
          "failed to sync qna question publication confirmation: postId={}, executionIntentId={}",
          event.postId(),
          event.executionIntentId(),
          e);
    }
  }

  @Override
  public void failQuestionCreate(
      Long postId,
      String executionIntentId,
      ExecutionIntentStatus terminalStatus,
      String failureReason) {
    eventPublisher.publishEvent(
        QnaQuestionPublicationSyncEvent.failed(
            postId, executionIntentId, terminalStatus.name(), failureReason));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleFailed(QnaQuestionPublicationSyncEvent.Failed event) {
    try {
      failQuestionCreateUseCase.failQuestionCreate(
          new SyncQuestionPublicationStateCommand(
              event.postId(),
              event.executionIntentId(),
              event.terminalStatus(),
              event.failureReason()));
    } catch (Exception e) {
      log.error(
          "failed to sync qna question publication failure: postId={}, executionIntentId={}, terminalStatus={}",
          event.postId(),
          event.executionIntentId(),
          event.terminalStatus(),
          e);
    }
  }

  public interface QnaQuestionPublicationSyncEvent {

    Long postId();

    String executionIntentId();

    static Confirmed confirmed(Long postId, String executionIntentId) {
      return new Confirmed(postId, executionIntentId);
    }

    static Failed failed(
        Long postId, String executionIntentId, String terminalStatus, String failureReason) {
      return new Failed(postId, executionIntentId, terminalStatus, failureReason);
    }

    record Confirmed(Long postId, String executionIntentId)
        implements QnaQuestionPublicationSyncEvent {}

    record Failed(
        Long postId, String executionIntentId, String terminalStatus, String failureReason)
        implements QnaQuestionPublicationSyncEvent {}
  }
}
