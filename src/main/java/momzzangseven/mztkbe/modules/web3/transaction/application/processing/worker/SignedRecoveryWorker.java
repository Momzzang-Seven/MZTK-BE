package momzzangseven.mztkbe.modules.web3.transaction.application.processing.worker;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.config.RewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.application.audit.detail.BroadcastAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.application.audit.detail.StateChangeAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.processing.worker.strategy.RetryStrategy;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class SignedRecoveryWorker extends AbstractWeb3Worker {

  private final Web3ContractPort web3ContractPort;

  private final String workerId = "signed-recovery-" + UUID.randomUUID().toString().substring(0, 8);

  public SignedRecoveryWorker(
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
    processBatchByStatus(
        Web3TxStatus.SIGNED,
        limit,
        workerId,
        claimTtlSeconds(),
        Web3TxFailureReason.BROADCAST_FAILED.code(),
        items ->
            forEachItem(items, this::processItem, Web3TxFailureReason.BROADCAST_FAILED.code()));
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
        new BroadcastAuditDetail(broadcast.success(), broadcast.txHash(), broadcast.failureReason())
            .toMap();
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
