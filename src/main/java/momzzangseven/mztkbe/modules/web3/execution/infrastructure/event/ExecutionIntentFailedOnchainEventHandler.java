package momzzangseven.mztkbe.modules.web3.execution.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentFailedOnchainUseCase;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionFailedOnchainEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnAnyExecutionEnabled
public class ExecutionIntentFailedOnchainEventHandler {

  private final MarkExecutionIntentFailedOnchainUseCase markExecutionIntentFailedOnchainUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(Web3TransactionFailedOnchainEvent event) {
    try {
      markExecutionIntentFailedOnchainUseCase.execute(event.transactionId(), event.failureReason());
    } catch (Exception e) {
      log.error(
          "failed to mark execution intent failed onchain after transaction commit: transactionId={}",
          event.transactionId(),
          e);
    }
  }
}
