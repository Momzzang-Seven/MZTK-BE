package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.answer;

import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.answer.application.dto.SyncAnswerPublicationStateCommand;
import momzzangseven.mztkbe.modules.answer.application.port.in.ConfirmAnswerSubmittedUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.FailAnswerSubmitUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAnswerPublicationSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionIntentStatus;
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
public class QnaAnswerPublicationSyncAdapter implements QnaAnswerPublicationSyncPort {

  private final ApplicationEventPublisher eventPublisher;
  private final ConfirmAnswerSubmittedUseCase confirmAnswerSubmittedUseCase;
  private final FailAnswerSubmitUseCase failAnswerSubmitUseCase;

  public QnaAnswerPublicationSyncAdapter(
      ApplicationEventPublisher eventPublisher,
      @Lazy ConfirmAnswerSubmittedUseCase confirmAnswerSubmittedUseCase,
      @Lazy FailAnswerSubmitUseCase failAnswerSubmitUseCase) {
    this.eventPublisher = eventPublisher;
    this.confirmAnswerSubmittedUseCase = confirmAnswerSubmittedUseCase;
    this.failAnswerSubmitUseCase = failAnswerSubmitUseCase;
  }

  @Override
  public void confirmAnswerSubmitted(Long answerId, String executionIntentId) {
    eventPublisher.publishEvent(AnswerPublicationSyncEvent.confirmed(answerId, executionIntentId));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleConfirmed(AnswerPublicationSyncEvent.Confirmed event) {
    try {
      confirmAnswerSubmittedUseCase.confirmAnswerSubmitted(
          new SyncAnswerPublicationStateCommand(
              event.answerId(), event.executionIntentId(), null, null));
    } catch (Exception e) {
      log.error(
          "failed to sync qna answer publication confirmation: answerId={}, executionIntentId={}",
          event.answerId(),
          event.executionIntentId(),
          e);
    }
  }

  @Override
  public void failAnswerSubmit(
      Long answerId,
      String executionIntentId,
      QnaExecutionIntentStatus terminalStatus,
      String failureReason) {
    eventPublisher.publishEvent(
        AnswerPublicationSyncEvent.failed(
            answerId, executionIntentId, terminalStatus.name(), failureReason));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleFailed(AnswerPublicationSyncEvent.Failed event) {
    try {
      failAnswerSubmitUseCase.failAnswerSubmit(
          new SyncAnswerPublicationStateCommand(
              event.answerId(),
              event.executionIntentId(),
              event.terminalStatus(),
              event.failureReason()));
    } catch (Exception e) {
      log.error(
          "failed to sync qna answer publication failure: answerId={}, executionIntentId={}, terminalStatus={}",
          event.answerId(),
          event.executionIntentId(),
          event.terminalStatus(),
          e);
    }
  }

  public interface AnswerPublicationSyncEvent {

    Long answerId();

    String executionIntentId();

    static Confirmed confirmed(Long answerId, String executionIntentId) {
      return new Confirmed(answerId, executionIntentId);
    }

    static Failed failed(
        Long answerId, String executionIntentId, String terminalStatus, String failureReason) {
      return new Failed(answerId, executionIntentId, terminalStatus, failureReason);
    }

    record Confirmed(Long answerId, String executionIntentId)
        implements AnswerPublicationSyncEvent {}

    record Failed(
        Long answerId, String executionIntentId, String terminalStatus, String failureReason)
        implements AnswerPublicationSyncEvent {}
  }
}
