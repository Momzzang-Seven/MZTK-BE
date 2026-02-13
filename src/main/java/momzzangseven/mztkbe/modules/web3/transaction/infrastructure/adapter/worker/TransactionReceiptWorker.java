package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.config.RewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.ReceiptPollAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.StateChangeAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.strategy.RetryStrategy;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Web3ContractPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class TransactionReceiptWorker extends AbstractWeb3Worker {

  private final Web3ContractPort web3ContractPort;

  private final String workerId = "receipt-" + UUID.randomUUID().toString().substring(0, 8);

  public TransactionReceiptWorker(
      LoadTransactionWorkPort loadTransactionWorkPort,
      UpdateTransactionPort updateTransactionPort,
      RecordTransactionAuditPort recordTransactionAuditPort,
      Web3ContractPort web3ContractPort,
      RewardTokenProperties rewardTokenProperties,
      RetryStrategy retryStrategy) {
    super(
        loadTransactionWorkPort,
        updateTransactionPort,
        recordTransactionAuditPort,
        rewardTokenProperties,
        retryStrategy);
    this.web3ContractPort = web3ContractPort;
  }

  @Scheduled(fixedDelay = 1000L)
  public void run() {
    processBatch(20);
  }

  void processBatch(int limit) {
    int claimTtlSeconds =
        Math.max(claimTtlSeconds(), rewardTokenProperties.getWorker().getReceiptTimeoutSeconds());
    processBatchByStatus(
        Web3TxStatus.PENDING,
        limit,
        workerId,
        claimTtlSeconds,
        Web3TxFailureReason.RPC_UNAVAILABLE.code(),
        items -> forEachItem(items, this::processItem, Web3TxFailureReason.RPC_UNAVAILABLE.code()));
  }

  private void processItem(LoadTransactionWorkPort.TransactionWorkItem item) {
    String txHash = item.txHash();
    if (txHash == null || txHash.isBlank()) {
      updateTransactionPort.updateStatus(
          item.transactionId(),
          Web3TxStatus.UNCONFIRMED,
          txHash,
          Web3TxFailureReason.RECEIPT_TIMEOUT.code());
      auditStateChange(item.transactionId(), Web3TxStatus.PENDING, Web3TxStatus.UNCONFIRMED);
      return;
    }

    int timeoutSeconds = rewardTokenProperties.getWorker().getReceiptTimeoutSeconds();
    long elapsedSeconds = elapsedSeconds(item);
    if (timeoutSeconds <= 0 || elapsedSeconds >= timeoutSeconds) {
      timeout(item.transactionId(), txHash, timeoutSeconds);
      return;
    }

    Web3ContractPort.ReceiptResult receipt = web3ContractPort.getReceipt(txHash);
    auditReceiptPoll(item.transactionId(), 1, elapsedSeconds, receipt);

    if (receipt.found()) {
      if (Boolean.TRUE.equals(receipt.success())) {
        updateTransactionPort.updateStatus(
            item.transactionId(), Web3TxStatus.SUCCEEDED, txHash, null);
        auditStateChange(item.transactionId(), Web3TxStatus.PENDING, Web3TxStatus.SUCCEEDED);
      } else {
        updateTransactionPort.updateStatus(
            item.transactionId(), Web3TxStatus.FAILED_ONCHAIN, txHash, "RECEIPT_STATUS_0");
        auditStateChange(item.transactionId(), Web3TxStatus.PENDING, Web3TxStatus.FAILED_ONCHAIN);
      }
      return;
    }

    if (receipt.rpcError()) {
      retry(
          item.transactionId(),
          receipt.failureReason() != null
              ? receipt.failureReason()
              : Web3TxFailureReason.RPC_UNAVAILABLE.code());
      return;
    }

    scheduleNextPoll(item.transactionId());
  }

  private void scheduleNextPoll(Long transactionId) {
    int pollMinSeconds = rewardTokenProperties.getWorker().getReceiptPollMinSeconds();
    int pollMaxSeconds = rewardTokenProperties.getWorker().getReceiptPollMaxSeconds();
    int nextPollSeconds = Math.max(1, Math.min(pollMinSeconds, pollMaxSeconds));

    updateTransactionPort.scheduleRetry(
        transactionId, null, LocalDateTime.now().plusSeconds(nextPollSeconds));
  }

  private long elapsedSeconds(LoadTransactionWorkPort.TransactionWorkItem item) {
    LocalDateTime baseline = item.broadcastedAt();
    if (baseline == null) {
      return Long.MAX_VALUE;
    }
    return Math.max(0, Duration.between(baseline, LocalDateTime.now()).getSeconds());
  }

  private void timeout(Long transactionId, String txHash, int timeoutSeconds) {
    String timeoutReason = Web3TxFailureReason.RECEIPT_TIMEOUT.code() + "_" + timeoutSeconds + "S";
    updateTransactionPort.updateStatus(
        transactionId, Web3TxStatus.UNCONFIRMED, txHash, timeoutReason);
    auditStateChange(transactionId, Web3TxStatus.PENDING, Web3TxStatus.UNCONFIRMED);
  }

  private void auditReceiptPoll(
      Long transactionId,
      int attempt,
      long elapsedSeconds,
      Web3ContractPort.ReceiptResult receipt) {
    Map<String, Object> detail =
        new ReceiptPollAuditDetail(
                attempt,
                elapsedSeconds,
                receipt.found(),
                receipt.rpcError(),
                receipt.failureReason())
            .toMap();

    audit(transactionId, Web3TransactionAuditEventType.RECEIPT_POLL, receipt.rpcAlias(), detail);
  }

  private void auditStateChange(Long transactionId, Web3TxStatus from, Web3TxStatus to) {
    audit(
        transactionId,
        Web3TransactionAuditEventType.STATE_CHANGE,
        null,
        new StateChangeAuditDetail(from, to).toMap());
  }

  @Override
  protected List<Class<? extends Throwable>> nonRetryableExceptions() {
    return List.of(Web3InvalidInputException.class, Web3TransactionStateInvalidException.class);
  }
}
