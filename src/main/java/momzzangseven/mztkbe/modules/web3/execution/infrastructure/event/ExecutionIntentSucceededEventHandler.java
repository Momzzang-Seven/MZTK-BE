package momzzangseven.mztkbe.modules.web3.execution.infrastructure.event;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionSucceededEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class ExecutionIntentSucceededEventHandler {

  private final MarkExecutionIntentSucceededUseCase markExecutionIntentSucceededUseCase;

  @EventListener
  public void handle(Web3TransactionSucceededEvent event) {
    markExecutionIntentSucceededUseCase.execute(event.transactionId());
  }
}
