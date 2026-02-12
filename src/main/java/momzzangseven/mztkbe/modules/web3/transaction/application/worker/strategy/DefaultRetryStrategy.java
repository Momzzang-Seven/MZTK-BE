package momzzangseven.mztkbe.modules.web3.transaction.application.worker.strategy;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.config.RewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
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
      RewardTokenProperties properties, LoadTransactionWorkPort.TransactionWorkItem item) {
    int backoffSeconds = Math.max(1, properties.getWorker().getRetryBackoffSeconds());
    return LocalDateTime.now().plusSeconds(backoffSeconds);
  }
}
