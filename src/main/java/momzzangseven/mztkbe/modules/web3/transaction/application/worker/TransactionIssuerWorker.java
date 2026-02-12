package momzzangseven.mztkbe.modules.web3.transaction.application.worker;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.ReserveNoncePort;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.config.RewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class TransactionIssuerWorker {

  private static final int DEFAULT_BATCH_SIZE = 20;

  private final LoadTransactionWorkPort loadTransactionWorkPort;
  private final UpdateTransactionPort updateTransactionPort;
  private final RecordTransactionAuditPort recordTransactionAuditPort;
  private final LoadTreasuryKeyPort loadTreasuryKeyPort;
  private final ReserveNoncePort reserveNoncePort;
  private final Web3ContractPort web3ContractPort;
  private final RewardTokenProperties rewardTokenProperties;
  private final Web3CoreProperties web3CoreProperties;

  private final String workerId = "issuer-" + UUID.randomUUID().toString().substring(0, 8);

  @Scheduled(fixedDelay = 1000L)
  public void run() {
    processBatch(DEFAULT_BATCH_SIZE);
  }

  void processBatch(int limit) {
    Duration claimTtl = Duration.ofSeconds(rewardTokenProperties.getWorker().getClaimTtlSeconds());
    List<LoadTransactionWorkPort.TransactionWorkItem> items =
        loadTransactionWorkPort.claimByStatus(Web3TxStatus.CREATED, limit, workerId, claimTtl);
    if (items.isEmpty()) {
      return;
    }

    LoadTreasuryKeyPort.TreasuryKeyMaterial treasuryKey = loadTreasuryKeyPort.load().orElse(null);
    if (treasuryKey == null) {
      items.forEach(
          item ->
              failPrevalidate(
                  item.transactionId(), Web3TxFailureReason.TREASURY_KEY_MISSING.code(), false));
      return;
    }

    for (LoadTransactionWorkPort.TransactionWorkItem item : items) {
      try {
        processItem(item, treasuryKey);
      } catch (Exception e) {
        log.warn("Issuer worker failed for txId={}", item.transactionId(), e);
        retry(item.transactionId(), Web3TxFailureReason.RPC_UNAVAILABLE.code());
      }
    }
  }

  private void processItem(
      LoadTransactionWorkPort.TransactionWorkItem item,
      LoadTreasuryKeyPort.TreasuryKeyMaterial treasuryKey) {
    Web3ContractPort.PrevalidateResult prevalidateResult =
        web3ContractPort.prevalidate(
            new Web3ContractPort.PrevalidateCommand(
                treasuryKey.treasuryAddress(), item.toAddress(), item.amountWei()));

    Map<String, Object> prevalidateDetail = new LinkedHashMap<>();
    if (prevalidateResult.detail() != null) {
      prevalidateDetail.putAll(prevalidateResult.detail());
    }
    prevalidateDetail.put("ok", prevalidateResult.ok());
    prevalidateDetail.put("failureReason", prevalidateResult.failureReason());
    audit(item.transactionId(), Web3TransactionAuditEventType.PREVALIDATE, null, prevalidateDetail);

    if (!prevalidateResult.ok()) {
      failPrevalidate(
          item.transactionId(), prevalidateResult.failureReason(), prevalidateResult.retryable());
      return;
    }

    long nonce = resolveNonce(item, treasuryKey.treasuryAddress());
    Web3ContractPort.SignedTransaction signed =
        web3ContractPort.signTransfer(
            new Web3ContractPort.SignTransferCommand(
                treasuryKey.privateKeyHex(),
                rewardTokenProperties.getTokenContractAddress(),
                item.toAddress(),
                item.amountWei(),
                nonce,
                web3CoreProperties.getChainId(),
                prevalidateResult.gasLimit(),
                prevalidateResult.maxPriorityFeePerGas(),
                prevalidateResult.maxFeePerGas()));

    updateTransactionPort.markSigned(item.transactionId(), nonce, signed.rawTx(), signed.txHash());
    Map<String, Object> signDetail = new LinkedHashMap<>();
    signDetail.put("nonce", nonce);
    signDetail.put("txHash", signed.txHash());
    audit(item.transactionId(), Web3TransactionAuditEventType.SIGN, null, signDetail);
    auditStateChange(item.transactionId(), Web3TxStatus.CREATED, Web3TxStatus.SIGNED);

    Web3ContractPort.BroadcastResult broadcast =
        web3ContractPort.broadcast(new Web3ContractPort.BroadcastCommand(signed.rawTx()));
    Map<String, Object> broadcastDetail = new LinkedHashMap<>();
    broadcastDetail.put("success", broadcast.success());
    broadcastDetail.put("txHash", broadcast.txHash());
    broadcastDetail.put("failureReason", broadcast.failureReason());
    audit(
        item.transactionId(),
        Web3TransactionAuditEventType.BROADCAST,
        broadcast.rpcAlias(),
        broadcastDetail);

    if (broadcast.success()) {
      String txHash =
          (broadcast.txHash() == null || broadcast.txHash().isBlank())
              ? signed.txHash()
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

  private void failPrevalidate(Long transactionId, String failureReason, boolean retryable) {
    if (retryable) {
      retry(transactionId, failureReason);
      return;
    }
    updateTransactionPort.scheduleRetry(transactionId, failureReason, null);
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

  private long resolveNonce(
      LoadTransactionWorkPort.TransactionWorkItem item, String treasuryAddress) {
    if (item.nonce() != null) {
      return item.nonce();
    }

    long reservedNonce = reserveNoncePort.reserveNextNonce(treasuryAddress);
    updateTransactionPort.assignNonce(item.transactionId(), reservedNonce);
    return reservedNonce;
  }
}
