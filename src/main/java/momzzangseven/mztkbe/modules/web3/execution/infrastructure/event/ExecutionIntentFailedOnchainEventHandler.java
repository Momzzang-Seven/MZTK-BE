package momzzangseven.mztkbe.modules.web3.execution.infrastructure.event;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentFailedOnchainUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionFailedOnchainEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class ExecutionIntentFailedOnchainEventHandler {

  private final MarkExecutionIntentFailedOnchainUseCase markExecutionIntentFailedOnchainUseCase;

  @EventListener
  public void handle(Web3TransactionFailedOnchainEvent event) {
    markExecutionIntentFailedOnchainUseCase.execute(event.transactionId(), event.failureReason());
  }
}
