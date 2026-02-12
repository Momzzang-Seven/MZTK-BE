package momzzangseven.mztkbe.modules.web3.transaction.application.worker;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.config.RewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.application.auditdetail.BroadcastAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.application.auditdetail.PrevalidateAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.application.auditdetail.SignAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.application.auditdetail.StateChangeAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.IssueTransactionOperationsPort;
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
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class TransactionIssuerWorker extends AbstractWeb3Worker {

  private final IssueTransactionOperationsPort issueTransactionOperationsPort;
  private final Web3CoreProperties web3CoreProperties;

  private final String workerId = "issuer-" + UUID.randomUUID().toString().substring(0, 8);

  public TransactionIssuerWorker(
      LoadTransactionWorkPort loadTransactionWorkPort,
      UpdateTransactionPort updateTransactionPort,
      RecordTransactionAuditPort recordTransactionAuditPort,
      IssueTransactionOperationsPort issueTransactionOperationsPort,
      RewardTokenProperties rewardTokenProperties,
      Web3CoreProperties web3CoreProperties) {
    super(
        loadTransactionWorkPort,
        updateTransactionPort,
        recordTransactionAuditPort,
        rewardTokenProperties);
    this.issueTransactionOperationsPort = issueTransactionOperationsPort;
    this.web3CoreProperties = web3CoreProperties;
  }

  @Scheduled(fixedDelay = 1000L)
  public void run() {
    processBatch(20);
  }

  void processBatch(int limit) {
    processBatchByStatus(
        Web3TxStatus.CREATED,
        limit,
        workerId,
        claimTtlSeconds(),
        Web3TxFailureReason.RPC_UNAVAILABLE.code(),
        this::processBatchItems);
  }

  void processBatchItems(List<LoadTransactionWorkPort.TransactionWorkItem> items) {
    var treasuryKey = issueTransactionOperationsPort.loadTreasuryKey().orElse(null);
    if (treasuryKey == null) {
      items.forEach(
          item ->
              failPrevalidate(
                  item.transactionId(), Web3TxFailureReason.TREASURY_KEY_MISSING.code(), false));
      return;
    }

    forEachItem(
        items, item -> processItem(item, treasuryKey), Web3TxFailureReason.RPC_UNAVAILABLE.code());
  }

  private void processItem(
      LoadTransactionWorkPort.TransactionWorkItem item,
      LoadTreasuryKeyPort.TreasuryKeyMaterial treasuryKey) {
    Web3ContractPort.PrevalidateResult prevalidateResult =
        issueTransactionOperationsPort.prevalidate(
            new Web3ContractPort.PrevalidateCommand(
                treasuryKey.treasuryAddress(), item.toAddress(), item.amountWei()));

    Map<String, Object> prevalidateDetail =
        new PrevalidateAuditDetail(
                prevalidateResult.detail(),
                prevalidateResult.ok(),
                prevalidateResult.failureReason())
            .toMap();
    audit(item.transactionId(), Web3TransactionAuditEventType.PREVALIDATE, null, prevalidateDetail);

    if (!prevalidateResult.ok()) {
      failPrevalidate(
          item.transactionId(), prevalidateResult.failureReason(), prevalidateResult.retryable());
      return;
    }

    long nonce = resolveNonce(item, treasuryKey.treasuryAddress());
    Web3ContractPort.SignedTransaction signed =
        issueTransactionOperationsPort.signTransfer(
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
    Map<String, Object> signDetail = new SignAuditDetail(nonce, signed.txHash()).toMap();
    audit(item.transactionId(), Web3TransactionAuditEventType.SIGN, null, signDetail);
    auditStateChange(item.transactionId(), Web3TxStatus.CREATED, Web3TxStatus.SIGNED);

    Web3ContractPort.BroadcastResult broadcast =
        issueTransactionOperationsPort.broadcast(
            new Web3ContractPort.BroadcastCommand(signed.rawTx()));
    Map<String, Object> broadcastDetail =
        new BroadcastAuditDetail(broadcast.success(), broadcast.txHash(), broadcast.failureReason())
            .toMap();
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

  private void auditStateChange(Long transactionId, Web3TxStatus from, Web3TxStatus to) {
    audit(
        transactionId,
        Web3TransactionAuditEventType.STATE_CHANGE,
        null,
        new StateChangeAuditDetail(from, to).toMap());
  }

  private long resolveNonce(
      LoadTransactionWorkPort.TransactionWorkItem item, String treasuryAddress) {
    if (item.nonce() != null) {
      return item.nonce();
    }

    long reservedNonce = issueTransactionOperationsPort.reserveNextNonce(treasuryAddress);
    updateTransactionPort.assignNonce(item.transactionId(), reservedNonce);
    return reservedNonce;
  }
}
