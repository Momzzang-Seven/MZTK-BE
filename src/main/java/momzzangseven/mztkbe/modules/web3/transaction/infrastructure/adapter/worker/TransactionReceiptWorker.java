package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.service.TransactionOutcomePublisher;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.ReceiptPollAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.StateChangeAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.strategy.RetryStrategy;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.TransactionRewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnAnyExecutionEnabled
/**
 * Worker that polls on-chain receipts for pending transactions.
 *
 * <p>It maps receipt outcomes to transaction status transitions and publishes execution intent
 * outcome events through {@link TransactionOutcomePublisher}.
 */
public class TransactionReceiptWorker extends AbstractWeb3Worker {

  private final Web3ContractPort web3ContractPort;
  private final TransactionOutcomePublisher transactionOutcomePublisher;
  private final ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase;
  private final Web3CoreProperties web3CoreProperties;
  private final Clock appClock;

  private final String workerId = "receipt-" + UUID.randomUUID().toString().substring(0, 8);

  public TransactionReceiptWorker(
      LoadTransactionWorkPort loadTransactionWorkPort,
      UpdateTransactionPort updateTransactionPort,
      RecordTransactionAuditPort recordTransactionAuditPort,
      Web3ContractPort web3ContractPort,
      TransactionOutcomePublisher transactionOutcomePublisher,
      ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase,
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
    this.nonceSlotLifecycleUseCase = nonceSlotLifecycleUseCase;
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
      markSlotStuck(item, timeoutReason);
      updateTransactionPort.updateStatus(
          item.transactionId(), Web3TxStatus.UNCONFIRMED, txHash, timeoutReason);
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
        markSlotConsumed(item, "RECEIPT_STATUS_1");
        transactionOutcomePublisher.markSucceededAndPublish(
            item.transactionId(),
            item.idempotencyKey(),
            item.referenceType(),
            item.referenceId(),
            item.fromUserId(),
            item.toUserId(),
            txHash);
        auditStateChange(item.transactionId(), Web3TxStatus.PENDING, Web3TxStatus.SUCCEEDED);
      } else {
        markSlotConsumed(item, "RECEIPT_STATUS_0");
        transactionOutcomePublisher.markFailedOnchainAndPublish(
            item.transactionId(),
            item.idempotencyKey(),
            item.referenceType(),
            item.referenceId(),
            item.fromUserId(),
            item.toUserId(),
            txHash,
            "RECEIPT_STATUS_0");
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

  private void markSlotConsumed(
      LoadTransactionWorkPort.TransactionWorkItem item, String consumedReason) {
    if (item.nonce() == null) {
      log.warn(
          "Skipping nonce slot consumed transition for txId={} without nonce",
          item.transactionId());
      return;
    }
    transitionSlotWithFallback(
        item,
        List.of(
            SponsorNonceSlotStatus.BROADCASTED,
            SponsorNonceSlotStatus.BROADCASTING,
            SponsorNonceSlotStatus.STUCK),
        SponsorNonceSlotStatus.CONSUMED,
        consumedReason);
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
    markSlotStuck(item, timeoutReason);
    updateTransactionPort.updateStatus(
        item.transactionId(), Web3TxStatus.UNCONFIRMED, txHash, timeoutReason);
    auditStateChange(item.transactionId(), Web3TxStatus.PENDING, Web3TxStatus.UNCONFIRMED);
  }

  private void markSlotStuck(LoadTransactionWorkPort.TransactionWorkItem item, String stuckReason) {
    if (item.nonce() == null) {
      log.warn(
          "Skipping nonce slot stuck transition for txId={} without nonce", item.transactionId());
      return;
    }
    transitionSlotWithFallback(
        item,
        List.of(SponsorNonceSlotStatus.BROADCASTED, SponsorNonceSlotStatus.BROADCASTING),
        SponsorNonceSlotStatus.STUCK,
        stuckReason);
  }

  private void transitionSlotWithFallback(
      LoadTransactionWorkPort.TransactionWorkItem item,
      List<SponsorNonceSlotStatus> fromStatuses,
      SponsorNonceSlotStatus toStatus,
      String reason) {
    Web3TransactionStateInvalidException lastStaleException = null;
    for (SponsorNonceSlotStatus fromStatus : fromStatuses) {
      try {
        nonceSlotLifecycleUseCase.transition(
            buildSlotTransition(item, fromStatus, toStatus, reason));
        return;
      } catch (Web3TransactionStateInvalidException e) {
        if (isSlotNotFound(e)) {
          log.warn(
              "Skipping nonce slot {} transition for non-slot txId={}: {}",
              toStatus,
              item.transactionId(),
              e.getMessage());
          return;
        }
        if (isStaleActual(e, toStatus)) {
          log.debug(
              "Skipping idempotent nonce slot {} transition for txId={}: {}",
              toStatus,
              item.transactionId(),
              e.getMessage());
          return;
        }
        if (isStaleTransition(e)) {
          lastStaleException = e;
          continue;
        }
        throw e;
      }
    }
    if (lastStaleException != null) {
      throw lastStaleException;
    }
  }

  private RecordSponsorNonceSlotTransitionCommand buildSlotTransition(
      LoadTransactionWorkPort.TransactionWorkItem item,
      SponsorNonceSlotStatus fromStatus,
      SponsorNonceSlotStatus toStatus,
      String reason) {
    RecordSponsorNonceSlotTransitionCommand.RecordSponsorNonceSlotTransitionCommandBuilder builder =
        RecordSponsorNonceSlotTransitionCommand.builder()
            .chainId(web3CoreProperties.getChainId())
            .fromAddress(item.fromAddress())
            .nonce(item.nonce())
            .fromStatus(fromStatus)
            .toStatus(toStatus)
            .activeTxId(item.transactionId())
            .stateChangedAt(LocalDateTime.now(appClock))
            .hasRawTx(item.signedRawTx() != null && !item.signedRawTx().isBlank())
            .hasTxHash(item.txHash() != null && !item.txHash().isBlank())
            .hasSigningEvidence(item.signedRawTx() != null && !item.signedRawTx().isBlank())
            .hasBroadcastEvidence(true);
    if (toStatus == SponsorNonceSlotStatus.CONSUMED) {
      return builder
          .consumedTxId(item.transactionId())
          .consumedReason(reason)
          .hasSigningEvidence(true)
          .hasReceiptEvidence(true)
          .build();
    }
    return builder.stuckReason(reason).build();
  }

  private boolean isSlotNotFound(Web3TransactionStateInvalidException e) {
    return e.getMessage() != null && e.getMessage().contains("nonce slot not found");
  }

  private boolean isStaleTransition(Web3TransactionStateInvalidException e) {
    return e.getMessage() != null && e.getMessage().contains("stale nonce slot transition");
  }

  private boolean isStaleActual(
      Web3TransactionStateInvalidException e, SponsorNonceSlotStatus actualStatus) {
    return isStaleTransition(e) && e.getMessage().contains("actual=" + actualStatus);
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
