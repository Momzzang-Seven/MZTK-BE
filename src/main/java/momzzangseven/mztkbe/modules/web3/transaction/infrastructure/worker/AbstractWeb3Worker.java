package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.worker;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.config.RewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.worker.strategy.RetryStrategy;

@Slf4j
@RequiredArgsConstructor
abstract class AbstractWeb3Worker {

  protected final LoadTransactionWorkPort loadTransactionWorkPort;
  protected final UpdateTransactionPort updateTransactionPort;
  protected final RecordTransactionAuditPort recordTransactionAuditPort;
  protected final RewardTokenProperties rewardTokenProperties;
  protected final RetryStrategy retryStrategy;

  protected void processBatchByStatus(
      Web3TxStatus status,
      int limit,
      String workerId,
      int claimTtlSeconds,
      String defaultFailureReason,
      Consumer<List<LoadTransactionWorkPort.TransactionWorkItem>> batchProcessor) {
    Duration claimTtl = Duration.ofSeconds(claimTtlSeconds);
    List<LoadTransactionWorkPort.TransactionWorkItem> items =
        loadTransactionWorkPort.claimByStatus(status, limit, workerId, claimTtl);
    if (items.isEmpty()) {
      return;
    }
    batchProcessor.accept(items);
  }

  protected void forEachItem(
      List<LoadTransactionWorkPort.TransactionWorkItem> items,
      Consumer<LoadTransactionWorkPort.TransactionWorkItem> itemProcessor,
      String defaultFailureReason) {
    for (LoadTransactionWorkPort.TransactionWorkItem item : items) {
      try {
        itemProcessor.accept(item);
      } catch (Exception e) {
        log.warn("{} worker failed for txId={}", workerTag(), item.transactionId(), e);
        if (!retryStrategy.shouldRetry(e, nonRetryableExceptions())) {
          failPermanently(item.transactionId(), defaultFailureReason);
          continue;
        }
        retry(item.transactionId(), defaultFailureReason, item);
      }
    }
  }

  protected List<Class<? extends Throwable>> nonRetryableExceptions() {
    return List.of();
  }

  protected void retry(Long transactionId, String failureReason) {
    retry(transactionId, failureReason, null);
  }

  protected void retry(
      Long transactionId, String failureReason, LoadTransactionWorkPort.TransactionWorkItem item) {
    LocalDateTime until = retryStrategy.nextRetryAt(rewardTokenProperties, item);
    updateTransactionPort.scheduleRetry(transactionId, failureReason, until);
  }

  protected void failPermanently(Long transactionId, String failureReason) {
    updateTransactionPort.scheduleRetry(transactionId, failureReason, null);
  }

  protected void audit(
      Long transactionId,
      Web3TransactionAuditEventType eventType,
      String rpcAlias,
      Map<String, Object> detail) {
    try {
      recordTransactionAuditPort.record(
          new RecordTransactionAuditPort.AuditCommand(transactionId, eventType, rpcAlias, detail));
    } catch (Exception e) {
      log.warn("Failed to record audit log: txId={}, event={}", transactionId, eventType, e);
    }
  }

  protected int claimTtlSeconds() {
    return rewardTokenProperties.getWorker().getClaimTtlSeconds();
  }

  protected String workerTag() {
    return getClass().getSimpleName();
  }
}
