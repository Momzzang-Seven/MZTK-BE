package momzzangseven.mztkbe.modules.web3.transaction.application.worker;

import java.time.Duration;
import java.time.LocalDateTime;
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
import momzzangseven.mztkbe.modules.web3.transaction.application.support.AuditDetailBuilder;
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
public class SignedRecoveryWorker {

  private static final int DEFAULT_BATCH_SIZE = 20;

  private final LoadTransactionWorkPort loadTransactionWorkPort;
  private final UpdateTransactionPort updateTransactionPort;
  private final RecordTransactionAuditPort recordTransactionAuditPort;
  private final Web3ContractPort web3ContractPort;
  private final RewardTokenProperties rewardTokenProperties;

  private final String workerId = "signed-recovery-" + UUID.randomUUID().toString().substring(0, 8);

  @Scheduled(fixedDelay = 1000L)
  public void run() {
    processBatch(DEFAULT_BATCH_SIZE);
  }

  void processBatch(int limit) {
    Duration claimTtl = Duration.ofSeconds(rewardTokenProperties.getWorker().getClaimTtlSeconds());
    List<LoadTransactionWorkPort.TransactionWorkItem> items =
        loadTransactionWorkPort.claimByStatus(Web3TxStatus.SIGNED, limit, workerId, claimTtl);
    if (items.isEmpty()) {
      return;
    }

    for (LoadTransactionWorkPort.TransactionWorkItem item : items) {
      try {
        processItem(item);
      } catch (Exception e) {
        log.warn("Signed recovery worker failed for txId={}", item.transactionId(), e);
        retry(item.transactionId(), Web3TxFailureReason.BROADCAST_FAILED.code());
      }
    }
  }

  private void processItem(LoadTransactionWorkPort.TransactionWorkItem item) {
    if (item.signedRawTx() == null || item.signedRawTx().isBlank()) {
      updateTransactionPort.scheduleRetry(
          item.transactionId(), Web3TxFailureReason.INVALID_SIGNED_TX.code(), null);
      return;
    }

    Web3ContractPort.BroadcastResult broadcast =
        web3ContractPort.broadcast(new Web3ContractPort.BroadcastCommand(item.signedRawTx()));

    Map<String, Object> detail =
        AuditDetailBuilder.create()
            .put("success", broadcast.success())
            .put("txHash", broadcast.txHash())
            .put("failureReason", broadcast.failureReason())
            .build();
    audit(
        item.transactionId(),
        Web3TransactionAuditEventType.BROADCAST,
        broadcast.rpcAlias(),
        detail);

    if (broadcast.success()) {
      String txHash =
          (broadcast.txHash() == null || broadcast.txHash().isBlank())
              ? item.txHash()
              : broadcast.txHash();
      updateTransactionPort.markPending(item.transactionId(), txHash);
      auditStateChange(item.transactionId(), Web3TxStatus.SIGNED, Web3TxStatus.PENDING);
      return;
    }

    retry(
        item.transactionId(),
        broadcast.failureReason() != null
            ? broadcast.failureReason()
            : Web3TxFailureReason.BROADCAST_FAILED.code());
  }

  private void retry(Long transactionId, String failureReason) {
    LocalDateTime until =
        LocalDateTime.now().plusSeconds(rewardTokenProperties.getWorker().getRetryBackoffSeconds());
    updateTransactionPort.scheduleRetry(transactionId, failureReason, until);
  }

  private void auditStateChange(Long transactionId, Web3TxStatus from, Web3TxStatus to) {
    audit(
        transactionId,
        Web3TransactionAuditEventType.STATE_CHANGE,
        null,
        AuditDetailBuilder.create().put("from", from).put("to", to).build());
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
