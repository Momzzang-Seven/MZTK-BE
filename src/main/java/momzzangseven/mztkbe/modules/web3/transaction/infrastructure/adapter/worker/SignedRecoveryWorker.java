package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotView;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.PersistSponsorNonceTransactionStateUseCase;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnAnyExecutionEnabled
public class SignedRecoveryWorker extends AbstractWeb3Worker {

  private final Web3ContractPort web3ContractPort;
  private final ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase;
  private final PersistSponsorNonceTransactionStateUseCase
      persistSponsorNonceTransactionStateUseCase;
  private final Clock appClock;

  private final String workerId = "signed-recovery-" + UUID.randomUUID().toString().substring(0, 8);

  public SignedRecoveryWorker(
      LoadTransactionWorkPort loadTransactionWorkPort,
      UpdateTransactionPort updateTransactionPort,
      RecordTransactionAuditPort recordTransactionAuditPort,
      Web3ContractPort web3ContractPort,
      ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase,
      PersistSponsorNonceTransactionStateUseCase persistSponsorNonceTransactionStateUseCase,
      TransactionRewardTokenProperties rewardTokenProperties,
      RetryStrategy retryStrategy,
      Clock appClock) {
    super(
        loadTransactionWorkPort,
        updateTransactionPort,
        recordTransactionAuditPort,
        rewardTokenProperties,
        retryStrategy);
    this.web3ContractPort = web3ContractPort;
    this.nonceSlotLifecycleUseCase = nonceSlotLifecycleUseCase;
    this.persistSponsorNonceTransactionStateUseCase = persistSponsorNonceTransactionStateUseCase;
    this.appClock = appClock;
  }

  @Scheduled(fixedDelayString = "${web3.transaction.signed-recovery.fixed-delay:1000}")
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

    SlotBroadcastPreparation slotPreparation = prepareSlotForBroadcast(item);
    if (slotPreparation == SlotBroadcastPreparation.STOP) {
      return;
    }
    if (slotPreparation == SlotBroadcastPreparation.MARK_PENDING_WITHOUT_BROADCAST) {
      if (markPendingWithoutBroadcast(item)) {
        auditStateChange(item.transactionId(), Web3TxStatus.SIGNED, Web3TxStatus.PENDING);
      }
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

    if (broadcast.success() || isBroadcastAlreadyKnown(broadcast)) {
      String txHash =
          (broadcast.txHash() == null || broadcast.txHash().isBlank())
              ? item.txHash()
              : broadcast.txHash();
      markPendingAfterBroadcast(item, txHash, slotPreparation);
      auditStateChange(item.transactionId(), Web3TxStatus.SIGNED, Web3TxStatus.PENDING);
      return;
    }
    if (isBroadcastNonceTooLow(broadcast)) {
      markBroadcastingSlotOperatorReview(item, Web3TxFailureReason.BROADCAST_NONCE_TOO_LOW.code());
      updateTransactionPort.scheduleRetry(
          item.transactionId(), Web3TxFailureReason.BROADCAST_NONCE_TOO_LOW.code(), null);
      return;
    }

    retry(
        item.transactionId(),
        broadcast.failureReason() != null
            ? broadcast.failureReason()
            : Web3TxFailureReason.BROADCAST_FAILED.code());
  }

  private boolean isBroadcastAlreadyKnown(Web3ContractPort.BroadcastResult broadcast) {
    return broadcast != null
        && Web3TxFailureReason.BROADCAST_ALREADY_KNOWN.code().equals(broadcast.failureReason());
  }

  private boolean isBroadcastNonceTooLow(Web3ContractPort.BroadcastResult broadcast) {
    return broadcast != null
        && Web3TxFailureReason.BROADCAST_NONCE_TOO_LOW.code().equals(broadcast.failureReason());
  }

  private void auditStateChange(Long transactionId, Web3TxStatus from, Web3TxStatus to) {
    audit(
        transactionId,
        Web3TransactionAuditEventType.STATE_CHANGE,
        null,
        new StateChangeAuditDetail(from, to).toMap());
  }

  private SlotBroadcastPreparation prepareSlotForBroadcast(
      LoadTransactionWorkPort.TransactionWorkItem item) {
    if (item.nonce() == null) {
      return SlotBroadcastPreparation.SKIP_SLOT_AFTER_SUCCESS;
    }
    try {
      nonceSlotLifecycleUseCase.transition(
          RecordSponsorNonceSlotTransitionCommand.builder()
              .chainId(item.chainId())
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
              .hasTxHash(item.txHash() != null && !item.txHash().isBlank())
              .hasSigningEvidence(true)
              .build());
      return SlotBroadcastPreparation.MARK_BROADCASTED_AFTER_SUCCESS;
    } catch (Web3TransactionStateInvalidException e) {
      if (isSlotNotFound(e)) {
        log.debug(
            "Broadcasting signed recovery tx without nonce slot: txId={}, reason={}",
            item.transactionId(),
            e.getMessage());
        return SlotBroadcastPreparation.SKIP_SLOT_AFTER_SUCCESS;
      }
      if (isStaleActual(e, SponsorNonceSlotStatus.BROADCASTING)) {
        if (!ownsNonceSlotInStatus(item, SponsorNonceSlotStatus.BROADCASTING)) {
          scheduleStaleSlotRetry(item, SponsorNonceSlotStatus.BROADCASTING, e);
          return SlotBroadcastPreparation.STOP;
        }
        log.debug(
            "Using existing nonce slot broadcasting transition for txId={}: {}",
            item.transactionId(),
            e.getMessage());
        return SlotBroadcastPreparation.MARK_BROADCASTED_AFTER_SUCCESS;
      }
      if (isStaleActual(e, SponsorNonceSlotStatus.BROADCASTED)) {
        if (!ownsNonceSlotInStatus(item, SponsorNonceSlotStatus.BROADCASTED)) {
          scheduleStaleSlotRetry(item, SponsorNonceSlotStatus.BROADCASTED, e);
          return SlotBroadcastPreparation.STOP;
        }
        log.debug(
            "Using already-broadcasted nonce slot for txId={}: {}",
            item.transactionId(),
            e.getMessage());
        return SlotBroadcastPreparation.MARK_PENDING_WITHOUT_BROADCAST;
      }
      if (isStaleTransition(e)) {
        log.warn(
            "Refusing to broadcast signed tx because nonce slot is not broadcastable: txId={}, reason={}",
            item.transactionId(),
            e.getMessage());
        updateTransactionPort.scheduleRetry(
            item.transactionId(), Web3TxFailureReason.SPONSOR_NONCE_STALE_RESERVATION.code(), null);
        return SlotBroadcastPreparation.STOP;
      }
      throw e;
    }
  }

  private boolean ownsNonceSlotInStatus(
      LoadTransactionWorkPort.TransactionWorkItem item, SponsorNonceSlotStatus status) {
    if (item.nonce() == null || item.fromAddress() == null || item.fromAddress().isBlank()) {
      return false;
    }
    return nonceSlotLifecycleUseCase
        .loadSlotForReview(item.chainId(), item.fromAddress(), item.nonce())
        .filter(slot -> isOwnedSlot(slot, item.transactionId(), status))
        .isPresent();
  }

  private boolean isOwnedSlot(
      SponsorNonceSlotView slot, Long transactionId, SponsorNonceSlotStatus status) {
    return slot.status() == status
        && transactionId != null
        && transactionId.equals(slot.activeTxId());
  }

  private void scheduleStaleSlotRetry(
      LoadTransactionWorkPort.TransactionWorkItem item,
      SponsorNonceSlotStatus actualStatus,
      Web3TransactionStateInvalidException cause) {
    log.warn(
        "Refusing to reuse stale nonce slot state because it is not owned by tx: txId={}, nonce={}, actual={}, reason={}",
        item.transactionId(),
        item.nonce(),
        actualStatus,
        cause.getMessage());
    updateTransactionPort.scheduleRetry(
        item.transactionId(), Web3TxFailureReason.SPONSOR_NONCE_STALE_RESERVATION.code(), null);
  }

  private void markPendingAfterBroadcast(
      LoadTransactionWorkPort.TransactionWorkItem item,
      String txHash,
      SlotBroadcastPreparation slotPreparation) {
    if (slotPreparation == SlotBroadcastPreparation.MARK_BROADCASTED_AFTER_SUCCESS) {
      persistSponsorNonceTransactionStateUseCase.markPending(
          new PersistSponsorNonceTransactionStateUseCase.SponsorNoncePendingCommand(
              item.transactionId(),
              item.chainId(),
              item.fromAddress(),
              item.nonce(),
              null,
              txHash,
              LocalDateTime.now(appClock)));
      return;
    }
    persistSponsorNonceTransactionStateUseCase.markPendingWithoutSlotTransition(
        new PersistSponsorNonceTransactionStateUseCase.TransactionPendingCommand(
            item.transactionId(), txHash));
  }

  private boolean markPendingWithoutBroadcast(LoadTransactionWorkPort.TransactionWorkItem item) {
    if (item.txHash() == null || item.txHash().isBlank()) {
      log.warn(
          "Cannot recover already-broadcasted nonce slot without tx hash: txId={}",
          item.transactionId());
      updateTransactionPort.scheduleRetry(
          item.transactionId(),
          Web3TxFailureReason.SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED.code(),
          null);
      return false;
    }
    persistSponsorNonceTransactionStateUseCase.markPendingWithoutSlotTransition(
        new PersistSponsorNonceTransactionStateUseCase.TransactionPendingCommand(
            item.transactionId(), item.txHash()));
    return true;
  }

  private void markSignedSlotOperatorReview(
      LoadTransactionWorkPort.TransactionWorkItem item, String terminalReason) {
    if (item.nonce() == null) {
      return;
    }
    try {
      nonceSlotLifecycleUseCase.transition(
          RecordSponsorNonceSlotTransitionCommand.builder()
              .chainId(item.chainId())
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

  private void markBroadcastingSlotOperatorReview(
      LoadTransactionWorkPort.TransactionWorkItem item, String terminalReason) {
    if (item.nonce() == null) {
      return;
    }
    try {
      nonceSlotLifecycleUseCase.transition(
          RecordSponsorNonceSlotTransitionCommand.builder()
              .chainId(item.chainId())
              .fromAddress(item.fromAddress())
              .nonce(item.nonce())
              .fromStatus(SponsorNonceSlotStatus.BROADCASTING)
              .toStatus(SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED)
              .activeTxId(item.transactionId())
              .stateChangedAt(LocalDateTime.now(appClock))
              .terminalReason(terminalReason)
              .hasRawTx(true)
              .hasTxHash(item.txHash() != null && !item.txHash().isBlank())
              .hasSigningEvidence(true)
              .hasBroadcastEvidence(true)
              .build());
    } catch (Web3TransactionStateInvalidException e) {
      if (isSlotNotFound(e)
          || isStaleActual(e, SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED)
          || isStaleActual(e, SponsorNonceSlotStatus.CONSUMED)
          || isStaleActual(e, SponsorNonceSlotStatus.CONSUMED_UNKNOWN)
          || isStaleActual(e, SponsorNonceSlotStatus.STUCK)) {
        log.debug(
            "Skipping broadcasting recovery operator-review transition for txId={}: {}",
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

  private boolean isStaleTransition(Web3TransactionStateInvalidException e) {
    return e.getMessage() != null && e.getMessage().contains("stale nonce slot transition");
  }

  private boolean isStaleActual(
      Web3TransactionStateInvalidException e, SponsorNonceSlotStatus actualStatus) {
    return isStaleTransition(e) && e.getMessage().contains("actual=" + actualStatus);
  }

  @Override
  protected List<Class<? extends Throwable>> nonRetryableExceptions() {
    return List.of(Web3InvalidInputException.class, Web3TransactionStateInvalidException.class);
  }

  @Override
  protected String permanentFailureReason(Throwable throwable, String defaultFailureReason) {
    if (throwable instanceof Web3TransactionStateInvalidException) {
      return Web3TxFailureReason.SPONSOR_NONCE_STALE_RESERVATION.code();
    }
    if (throwable instanceof Web3InvalidInputException) {
      return Web3TxFailureReason.INVALID_SIGNED_TX.code();
    }
    return super.permanentFailureReason(throwable, defaultFailureReason);
  }

  private enum SlotBroadcastPreparation {
    MARK_BROADCASTED_AFTER_SUCCESS,
    MARK_PENDING_WITHOUT_BROADCAST,
    SKIP_SLOT_AFTER_SUCCESS,
    STOP
  }
}
