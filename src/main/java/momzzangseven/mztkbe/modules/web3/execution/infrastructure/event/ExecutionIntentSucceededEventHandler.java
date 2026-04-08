package momzzangseven.mztkbe.modules.web3.execution.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionSucceededEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class ExecutionIntentSucceededEventHandler {

  private final MarkExecutionIntentSucceededUseCase markExecutionIntentSucceededUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(Web3TransactionSucceededEvent event) {
    try {
      markExecutionIntentSucceededUseCase.execute(event.transactionId());
    } catch (Exception e) {
      log.error(
          "failed to mark execution intent succeeded after transaction commit: transactionId={}",
          event.transactionId(),
          e);
    }
  }
}
