package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentPendingOnchainUseCase;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.BroadcastAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.StateChangeAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.strategy.RetryStrategy;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.TransactionRewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnAnyExecutionEnabled
public class SignedRecoveryWorker extends AbstractWeb3Worker {

  private final Web3ContractPort web3ContractPort;
  private final MarkExecutionIntentPendingOnchainUseCase markExecutionIntentPendingOnchainUseCase;
  private final ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase;
  private final Web3CoreProperties web3CoreProperties;
  private final Clock appClock;

  private final String workerId = "signed-recovery-" + UUID.randomUUID().toString().substring(0, 8);

  public SignedRecoveryWorker(
      LoadTransactionWorkPort loadTransactionWorkPort,
      UpdateTransactionPort updateTransactionPort,
      RecordTransactionAuditPort recordTransactionAuditPort,
      Web3ContractPort web3ContractPort,
      MarkExecutionIntentPendingOnchainUseCase markExecutionIntentPendingOnchainUseCase,
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
    this.markExecutionIntentPendingOnchainUseCase = markExecutionIntentPendingOnchainUseCase;
    this.nonceSlotLifecycleUseCase = nonceSlotLifecycleUseCase;
    this.web3CoreProperties = web3CoreProperties;
    this.appClock = appClock;
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
      markSignedSlotOperatorReview(item, Web3TxFailureReason.INVALID_SIGNED_TX.code());
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
      markSlotBroadcasting(item, txHash);
      updateTransactionPort.markPending(item.transactionId(), txHash);
      markExecutionIntentPendingOnchainUseCase.execute(item.transactionId());
      markSlotBroadcasted(item);
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

  private void markSlotBroadcasting(
      LoadTransactionWorkPort.TransactionWorkItem item, String txHash) {
    if (item.nonce() == null) {
      return;
    }
    try {
      nonceSlotLifecycleUseCase.transition(
          RecordSponsorNonceSlotTransitionCommand.builder()
              .chainId(web3CoreProperties.getChainId())
              .fromAddress(item.fromAddress())
              .nonce(item.nonce())
              .fromStatus(SponsorNonceSlotStatus.SIGNED)
              .toStatus(SponsorNonceSlotStatus.BROADCASTING)
              .activeTxId(item.transactionId())
              .stateChangedAt(LocalDateTime.now(appClock))
              .broadcastRecoveryClaimOwner(workerId)
              .broadcastRecoveryClaimToken(UUID.randomUUID().toString())
              .broadcastRecoveryClaimExpiresAt(
                  LocalDateTime.now(appClock).plusSeconds(claimTtlSeconds()))
              .broadcastRecoveryAttemptCount(1)
              .hasRawTx(true)
              .hasTxHash(txHash != null && !txHash.isBlank())
              .hasSigningEvidence(true)
              .build());
    } catch (Web3TransactionStateInvalidException e) {
      if (isSlotNotFound(e)
          || isStaleActual(e, SponsorNonceSlotStatus.BROADCASTING)
          || isStaleActual(e, SponsorNonceSlotStatus.BROADCASTED)) {
        log.debug(
            "Skipping signed recovery broadcasting transition for txId={}: {}",
            item.transactionId(),
            e.getMessage());
        return;
      }
      throw e;
    }
  }

  private void markSignedSlotOperatorReview(
      LoadTransactionWorkPort.TransactionWorkItem item, String terminalReason) {
    if (item.nonce() == null) {
      return;
    }
    try {
      nonceSlotLifecycleUseCase.transition(
          RecordSponsorNonceSlotTransitionCommand.builder()
              .chainId(web3CoreProperties.getChainId())
              .fromAddress(item.fromAddress())
              .nonce(item.nonce())
              .fromStatus(SponsorNonceSlotStatus.SIGNED)
              .toStatus(SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED)
              .activeTxId(item.transactionId())
              .stateChangedAt(LocalDateTime.now(appClock))
              .terminalReason(terminalReason)
              .build());
    } catch (Web3TransactionStateInvalidException e) {
      if (isSlotNotFound(e)
          || isStaleActual(e, SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED)
          || isStaleActual(e, SponsorNonceSlotStatus.CONSUMED)
          || isStaleActual(e, SponsorNonceSlotStatus.CONSUMED_UNKNOWN)
          || isStaleActual(e, SponsorNonceSlotStatus.STUCK)) {
        log.debug(
            "Skipping signed recovery operator-review transition for txId={}: {}",
            item.transactionId(),
            e.getMessage());
        return;
      }
      throw e;
    }
  }

  private void markSlotBroadcasted(LoadTransactionWorkPort.TransactionWorkItem item) {
    if (item.nonce() == null) {
      return;
    }
    try {
      nonceSlotLifecycleUseCase.transition(
          RecordSponsorNonceSlotTransitionCommand.builder()
              .chainId(web3CoreProperties.getChainId())
              .fromAddress(item.fromAddress())
              .nonce(item.nonce())
              .fromStatus(SponsorNonceSlotStatus.BROADCASTING)
              .toStatus(SponsorNonceSlotStatus.BROADCASTED)
              .activeTxId(item.transactionId())
              .stateChangedAt(LocalDateTime.now(appClock))
              .hasRawTx(true)
              .hasTxHash(true)
              .hasSigningEvidence(true)
              .hasBroadcastEvidence(true)
              .build());
    } catch (Web3TransactionStateInvalidException e) {
      if (isSlotNotFound(e) || isStaleActual(e, SponsorNonceSlotStatus.BROADCASTED)) {
        log.debug(
            "Skipping signed recovery broadcasted transition for txId={}: {}",
            item.transactionId(),
            e.getMessage());
        return;
      }
      throw e;
    }
  }

  private boolean isSlotNotFound(Web3TransactionStateInvalidException e) {
    return e.getMessage() != null && e.getMessage().contains("nonce slot not found");
  }

  private boolean isStaleActual(
      Web3TransactionStateInvalidException e, SponsorNonceSlotStatus actualStatus) {
    return e.getMessage() != null
        && e.getMessage().contains("stale nonce slot transition")
        && e.getMessage().contains("actual=" + actualStatus);
  }

  @Override
  protected List<Class<? extends Throwable>> nonRetryableExceptions() {
    return List.of(Web3InvalidInputException.class, Web3TransactionStateInvalidException.class);
  }
}
