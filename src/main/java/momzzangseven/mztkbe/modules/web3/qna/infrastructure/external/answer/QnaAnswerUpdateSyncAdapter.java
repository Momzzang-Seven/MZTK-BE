package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.answer;

import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.answer.application.dto.SyncAnswerUpdateStateCommand;
import momzzangseven.mztkbe.modules.answer.application.port.in.ConfirmAnswerUpdateUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.FailAnswerUpdateUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAnswerUpdateSyncPort;
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
public class QnaAnswerUpdateSyncAdapter implements QnaAnswerUpdateSyncPort {

  private final ApplicationEventPublisher eventPublisher;
  private final ConfirmAnswerUpdateUseCase confirmAnswerUpdateUseCase;
  private final FailAnswerUpdateUseCase failAnswerUpdateUseCase;

  public QnaAnswerUpdateSyncAdapter(
      ApplicationEventPublisher eventPublisher,
      @Lazy ConfirmAnswerUpdateUseCase confirmAnswerUpdateUseCase,
      @Lazy FailAnswerUpdateUseCase failAnswerUpdateUseCase) {
    this.eventPublisher = eventPublisher;
    this.confirmAnswerUpdateUseCase = confirmAnswerUpdateUseCase;
    this.failAnswerUpdateUseCase = failAnswerUpdateUseCase;
  }

  @Override
  public void confirmAnswerUpdate(
      Long answerId, Long updateVersion, String updateToken, String executionIntentId) {
    eventPublisher.publishEvent(
        AnswerUpdateSyncEvent.confirmed(answerId, updateVersion, updateToken, executionIntentId));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleConfirmed(AnswerUpdateSyncEvent.Confirmed event) {
    try {
      confirmAnswerUpdateUseCase.confirmAnswerUpdate(
          new SyncAnswerUpdateStateCommand(
              event.answerId(),
              event.updateVersion(),
              event.updateToken(),
              event.executionIntentId(),
              null));
    } catch (Exception e) {
      log.error(
          "failed to sync qna answer update confirmation: answerId={}, updateVersion={}, executionIntentId={}",
          event.answerId(),
          event.updateVersion(),
          event.executionIntentId(),
          e);
    }
  }

  @Override
  public void failAnswerUpdate(
      Long answerId,
      Long updateVersion,
      String updateToken,
      String executionIntentId,
      String failureReason) {
    eventPublisher.publishEvent(
        AnswerUpdateSyncEvent.failed(
            answerId, updateVersion, updateToken, executionIntentId, failureReason));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleFailed(AnswerUpdateSyncEvent.Failed event) {
    try {
      failAnswerUpdateUseCase.failAnswerUpdate(
          new SyncAnswerUpdateStateCommand(
              event.answerId(),
              event.updateVersion(),
              event.updateToken(),
              event.executionIntentId(),
              event.failureReason()));
    } catch (Exception e) {
      log.error(
          "failed to sync qna answer update failure: answerId={}, updateVersion={}, executionIntentId={}",
          event.answerId(),
          event.updateVersion(),
          event.executionIntentId(),
          e);
    }
  }

  public interface AnswerUpdateSyncEvent {

    Long answerId();

    Long updateVersion();

    String updateToken();

    String executionIntentId();

    static Confirmed confirmed(
        Long answerId, Long updateVersion, String updateToken, String executionIntentId) {
      return new Confirmed(answerId, updateVersion, updateToken, executionIntentId);
    }

    static Failed failed(
        Long answerId,
        Long updateVersion,
        String updateToken,
        String executionIntentId,
        String failureReason) {
      return new Failed(answerId, updateVersion, updateToken, executionIntentId, failureReason);
    }

    record Confirmed(
        Long answerId, Long updateVersion, String updateToken, String executionIntentId)
        implements AnswerUpdateSyncEvent {}

    record Failed(
        Long answerId,
        Long updateVersion,
        String updateToken,
        String executionIntentId,
        String failureReason)
        implements AnswerUpdateSyncEvent {}
  }
}
