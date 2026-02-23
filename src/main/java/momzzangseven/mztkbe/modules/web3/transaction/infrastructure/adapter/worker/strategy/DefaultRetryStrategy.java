package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.strategy;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.TransactionRewardTokenProperties;
import org.springframework.stereotype.Component;

@Component
public class DefaultRetryStrategy implements RetryStrategy {

  @Override
  public boolean shouldRetry(
      Throwable throwable, List<Class<? extends Throwable>> nonRetryableExceptions) {
    return nonRetryableExceptions.stream().noneMatch(type -> type.isInstance(throwable));
  }

  @Override
  public LocalDateTime nextRetryAt(
      TransactionRewardTokenProperties properties,
      LoadTransactionWorkPort.TransactionWorkItem item) {
    int backoffSeconds = Math.max(1, properties.getWorker().getRetryBackoffSeconds());
    return LocalDateTime.now().plusSeconds(backoffSeconds);
  }
}
