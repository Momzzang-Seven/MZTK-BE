package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.strategy;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.TransactionRewardTokenProperties;

public interface RetryStrategy {

  boolean shouldRetry(Throwable throwable, List<Class<? extends Throwable>> nonRetryableExceptions);

  LocalDateTime nextRetryAt(
      TransactionRewardTokenProperties properties,
      LoadTransactionWorkPort.TransactionWorkItem item);
}
