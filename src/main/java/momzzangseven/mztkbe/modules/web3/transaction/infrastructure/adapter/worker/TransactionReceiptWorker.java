package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.PersistSponsorNonceTransactionStateUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.service.TransactionOutcomePublisher;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.ReceiptPollAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.StateChangeAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.strategy.RetryStrategy;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.TransactionRewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Worker that polls on-chain receipts for pending transactions.
 *
 * <p>It maps receipt outcomes to transaction status transitions and publishes execution intent
 * outcome events through {@link TransactionOutcomePublisher}.
 */
@Component
@ConditionalOnAnyExecutionEnabled
public class TransactionReceiptWorker extends AbstractWeb3Worker {

  private final Web3ContractPort web3ContractPort;
  private final TransactionOutcomePublisher transactionOutcomePublisher;
  private final PersistSponsorNonceTransactionStateUseCase
      persistSponsorNonceTransactionStateUseCase;
  private final Web3CoreProperties web3CoreProperties;
  private final Clock appClock;

  private final String workerId = "receipt-" + UUID.randomUUID().toString().substring(0, 8);

  public TransactionReceiptWorker(
      LoadTransactionWorkPort loadTransactionWorkPort,
      UpdateTransactionPort updateTransactionPort,
      RecordTransactionAuditPort recordTransactionAuditPort,
      Web3ContractPort web3ContractPort,
      TransactionOutcomePublisher transactionOutcomePublisher,
      PersistSponsorNonceTransactionStateUseCase persistSponsorNonceTransactionStateUseCase,
      TransactionRewardTokenProperties rewardTokenProperties,
      RetryStrategy retryStrategy,
      Web3CoreProperties web3CoreProperties,
      Clock appClock) {
    super(
        loadTransactionWorkPort,
        updateTransactionPort,
        recordTransactionAuditPort,
        rewardTokenProperties,
        retryStrategy);
    this.web3ContractPort = web3ContractPort;
    this.transactionOutcomePublisher = transactionOutcomePublisher;
    this.persistSponsorNonceTransactionStateUseCase = persistSponsorNonceTransactionStateUseCase;
    this.web3CoreProperties = web3CoreProperties;
    this.appClock = appClock;
  }

  @Scheduled(fixedDelay = 1000L)
  public void run() {
    processBatch(20);
  }

  /** Processes one bounded polling batch for {@code PENDING} transactions. */
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
      String timeoutReason = Web3TxFailureReason.RECEIPT_TIMEOUT.code() + "_MISSING_TX_HASH";
      markUnconfirmed(item, txHash, timeoutReason);
      auditStateChange(item.transactionId(), Web3TxStatus.PENDING, Web3TxStatus.UNCONFIRMED);
      return;
    }

    int timeoutSeconds = rewardTokenProperties.getWorker().getReceiptTimeoutSeconds();
    long elapsedSeconds = elapsedSeconds(item);
    if (timeoutSeconds <= 0 || elapsedSeconds >= timeoutSeconds) {
      timeout(item, txHash, timeoutSeconds);
      return;
    }

    Web3ContractPort.ReceiptResult receipt = web3ContractPort.getReceipt(txHash);
    auditReceiptPoll(item.transactionId(), 1, elapsedSeconds, receipt);

    if (receipt.found()) {
      if (Boolean.TRUE.equals(receipt.success())) {
        transactionOutcomePublisher.markSucceededWithNonceSlotAndPublish(
            item.transactionId(),
            item.idempotencyKey(),
            item.referenceType(),
            item.referenceId(),
            item.fromUserId(),
            item.toUserId(),
            txHash,
            nonceReceiptCommand(item, "RECEIPT_STATUS_1"));
        auditStateChange(item.transactionId(), Web3TxStatus.PENDING, Web3TxStatus.SUCCEEDED);
      } else {
        transactionOutcomePublisher.markFailedOnchainWithNonceSlotAndPublish(
            item.transactionId(),
            item.idempotencyKey(),
            item.referenceType(),
            item.referenceId(),
            item.fromUserId(),
            item.toUserId(),
            txHash,
            "RECEIPT_STATUS_0",
            nonceReceiptCommand(item, "RECEIPT_STATUS_0"));
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
        transactionId, null, LocalDateTime.now(appClock).plusSeconds(nextPollSeconds));
  }

  private long elapsedSeconds(LoadTransactionWorkPort.TransactionWorkItem item) {
    LocalDateTime baseline = item.broadcastedAt();
    if (baseline == null) {
      return Long.MAX_VALUE;
    }
    return Math.max(0, Duration.between(baseline, LocalDateTime.now(appClock)).getSeconds());
  }

  private void timeout(
      LoadTransactionWorkPort.TransactionWorkItem item, String txHash, int timeoutSeconds) {
    String timeoutReason = Web3TxFailureReason.RECEIPT_TIMEOUT.code() + "_" + timeoutSeconds + "S";
    markUnconfirmed(item, txHash, timeoutReason);
    auditStateChange(item.transactionId(), Web3TxStatus.PENDING, Web3TxStatus.UNCONFIRMED);
  }

  private void markUnconfirmed(
      LoadTransactionWorkPort.TransactionWorkItem item, String txHash, String timeoutReason) {
    persistSponsorNonceTransactionStateUseCase.markUnconfirmed(
        new PersistSponsorNonceTransactionStateUseCase.SponsorNonceUnconfirmedCommand(
            item.transactionId(),
            web3CoreProperties.getChainId(),
            item.fromAddress(),
            item.nonce(),
            txHash,
            timeoutReason,
            LocalDateTime.now(appClock)));
  }

  private TransactionOutcomePublisher.SponsorNonceReceiptCommand nonceReceiptCommand(
      LoadTransactionWorkPort.TransactionWorkItem item, String consumedReason) {
    if (item.nonce() == null) {
      return null;
    }
    return new TransactionOutcomePublisher.SponsorNonceReceiptCommand(
        web3CoreProperties.getChainId(),
        item.fromAddress(),
        item.nonce(),
        consumedReason,
        LocalDateTime.now(appClock));
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

  @Override
  protected String permanentFailureReason(Throwable throwable, String defaultFailureReason) {
    if (throwable instanceof Web3TransactionStateInvalidException) {
      return Web3TxFailureReason.SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED.code();
    }
    if (throwable instanceof Web3InvalidInputException) {
      return Web3TxFailureReason.PREVALIDATE_INVALID_COMMAND.code();
    }
    return super.permanentFailureReason(throwable, defaultFailureReason);
  }
}
