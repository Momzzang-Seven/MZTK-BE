package momzzangseven.mztkbe.modules.web3.transaction.application.processing.worker.strategy;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.config.RewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;

public interface RetryStrategy {

  boolean shouldRetry(Throwable throwable, List<Class<? extends Throwable>> nonRetryableExceptions);

  LocalDateTime nextRetryAt(
      RewardTokenProperties properties, LoadTransactionWorkPort.TransactionWorkItem item);
}
