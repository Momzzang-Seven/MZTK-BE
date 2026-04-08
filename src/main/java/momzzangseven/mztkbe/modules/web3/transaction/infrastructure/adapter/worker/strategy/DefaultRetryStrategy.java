package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.strategy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.TransactionRewardTokenProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
/** Default retry strategy using fixed backoff from reward-token worker configuration. */
public class DefaultRetryStrategy implements RetryStrategy {

  private final Clock appClock;

  @Override
  public boolean shouldRetry(
      Throwable throwable, List<Class<? extends Throwable>> nonRetryableExceptions) {
    return nonRetryableExceptions.stream().noneMatch(type -> type.isInstance(throwable));
  }

  /** Returns next retry timestamp based on configured backoff seconds. */
  @Override
  public LocalDateTime nextRetryAt(
      TransactionRewardTokenProperties properties,
      LoadTransactionWorkPort.TransactionWorkItem item) {
    int backoffSeconds = Math.max(1, properties.getWorker().getRetryBackoffSeconds());
    return LocalDateTime.now(appClock).plusSeconds(backoffSeconds);
  }
}
