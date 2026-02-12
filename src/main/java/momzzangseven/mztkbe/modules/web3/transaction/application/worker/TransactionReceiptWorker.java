package momzzangseven.mztkbe.modules.web3.transaction.application.worker;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.config.RewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class TransactionReceiptWorker {

  private static final int DEFAULT_BATCH_SIZE = 20;

  private final LoadTransactionWorkPort loadTransactionWorkPort;
  private final UpdateTransactionPort updateTransactionPort;
  private final RecordTransactionAuditPort recordTransactionAuditPort;
  private final Web3ContractPort web3ContractPort;
  private final RewardTokenProperties rewardTokenProperties;

  private final String workerId = "receipt-" + UUID.randomUUID().toString().substring(0, 8);

  @Scheduled(fixedDelay = 1000L)
  public void run() {
    processBatch(DEFAULT_BATCH_SIZE);
  }

  void processBatch(int limit) {
    int claimTtlSeconds =
        Math.max(
            rewardTokenProperties.getWorker().getClaimTtlSeconds(),
            rewardTokenProperties.getWorker().getReceiptTimeoutSeconds());
    Duration claimTtl = Duration.ofSeconds(claimTtlSeconds);
    List<LoadTransactionWorkPort.TransactionWorkItem> items =
        loadTransactionWorkPort.claimByStatus(Web3TxStatus.PENDING, limit, workerId, claimTtl);
    if (items.isEmpty()) {
      return;
    }

    for (LoadTransactionWorkPort.TransactionWorkItem item : items) {
      try {
        processItem(item);
      } catch (Exception e) {
        log.warn("Receipt worker failed for txId={}", item.transactionId(), e);
        retry(item.transactionId(), Web3TxFailureReason.RPC_UNAVAILABLE.code());
      }
    }
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

  private void retry(Long transactionId, String failureReason) {
    LocalDateTime until =
        LocalDateTime.now().plusSeconds(rewardTokenProperties.getWorker().getRetryBackoffSeconds());
    updateTransactionPort.scheduleRetry(transactionId, failureReason, until);
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
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("attempt", attempt);
    detail.put("elapsedSeconds", elapsedSeconds);
    detail.put("result", receipt.found() ? "receipt_found" : "receipt_missing");
    detail.put("rpcError", receipt.rpcError());
    detail.put("failureReason", receipt.failureReason());

    audit(transactionId, Web3TransactionAuditEventType.RECEIPT_POLL, receipt.rpcAlias(), detail);
  }

  private void auditStateChange(Long transactionId, Web3TxStatus from, Web3TxStatus to) {
    audit(
        transactionId,
        Web3TransactionAuditEventType.STATE_CHANGE,
        null,
        Map.of("from", from.name(), "to", to.name()));
  }

  private void audit(
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
}
