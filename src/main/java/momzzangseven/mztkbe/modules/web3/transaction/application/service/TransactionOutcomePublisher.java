package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotView;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionFailedOnchainEvent;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionSucceededEvent;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes domain outcome events in the same transaction as web3 status transitions.
 *
 * <p>This prevents "status updated but event not delivered" drift when sync handlers fail.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionOutcomePublisher {

  private final UpdateTransactionPort updateTransactionPort;
  private final ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public void markSucceededAndPublish(
      Long transactionId,
      String idempotencyKey,
      Web3ReferenceType referenceType,
      String referenceId,
      Long fromUserId,
      Long toUserId,
      String txHash) {
    validate(transactionId, idempotencyKey, referenceType, referenceId);
    updateTransactionPort.updateStatus(transactionId, Web3TxStatus.SUCCEEDED, txHash, null);
    eventPublisher.publishEvent(
        new Web3TransactionSucceededEvent(
            transactionId,
            idempotencyKey,
            referenceType,
            referenceId,
            fromUserId,
            toUserId,
            txHash));
  }

  @Transactional
  public void markSucceededWithNonceSlotAndPublish(
      Long transactionId,
      String idempotencyKey,
      Web3ReferenceType referenceType,
      String referenceId,
      Long fromUserId,
      Long toUserId,
      String txHash,
      SponsorNonceReceiptCommand nonceCommand) {
    validate(transactionId, idempotencyKey, referenceType, referenceId);
    markNonceSlotConsumed(transactionId, nonceCommand);
    updateTransactionPort.updateStatus(transactionId, Web3TxStatus.SUCCEEDED, txHash, null);
    eventPublisher.publishEvent(
        new Web3TransactionSucceededEvent(
            transactionId,
            idempotencyKey,
            referenceType,
            referenceId,
            fromUserId,
            toUserId,
            txHash));
  }

  @Transactional
  public void markFailedOnchainAndPublish(
      Long transactionId,
      String idempotencyKey,
      Web3ReferenceType referenceType,
      String referenceId,
      Long fromUserId,
      Long toUserId,
      String txHash,
      String failureReason) {
    validate(transactionId, idempotencyKey, referenceType, referenceId);
    if (failureReason == null || failureReason.isBlank()) {
      throw new Web3InvalidInputException("failureReason is required");
    }
    updateTransactionPort.updateStatus(
        transactionId, Web3TxStatus.FAILED_ONCHAIN, txHash, failureReason);
    eventPublisher.publishEvent(
        new Web3TransactionFailedOnchainEvent(
            transactionId,
            idempotencyKey,
            referenceType,
            referenceId,
            fromUserId,
            toUserId,
            txHash,
            failureReason));
  }

  @Transactional
  public void markFailedOnchainWithNonceSlotAndPublish(
      Long transactionId,
      String idempotencyKey,
      Web3ReferenceType referenceType,
      String referenceId,
      Long fromUserId,
      Long toUserId,
      String txHash,
      String failureReason,
      SponsorNonceReceiptCommand nonceCommand) {
    validate(transactionId, idempotencyKey, referenceType, referenceId);
    if (failureReason == null || failureReason.isBlank()) {
      throw new Web3InvalidInputException("failureReason is required");
    }
    markNonceSlotConsumed(transactionId, nonceCommand);
    updateTransactionPort.updateStatus(
        transactionId, Web3TxStatus.FAILED_ONCHAIN, txHash, failureReason);
    eventPublisher.publishEvent(
        new Web3TransactionFailedOnchainEvent(
            transactionId,
            idempotencyKey,
            referenceType,
            referenceId,
            fromUserId,
            toUserId,
            txHash,
            failureReason));
  }

  private void markNonceSlotConsumed(Long transactionId, SponsorNonceReceiptCommand command) {
    if (command == null || command.nonce() == null) {
      return;
    }
    Web3TransactionStateInvalidException lastStaleException = null;
    for (SponsorNonceSlotStatus fromStatus :
        List.of(
            SponsorNonceSlotStatus.BROADCASTED,
            SponsorNonceSlotStatus.BROADCASTING,
            SponsorNonceSlotStatus.STUCK)) {
      try {
        nonceSlotLifecycleUseCase.transition(
            RecordSponsorNonceSlotTransitionCommand.builder()
                .chainId(command.chainId())
                .fromAddress(command.fromAddress())
                .nonce(command.nonce())
                .fromStatus(fromStatus)
                .toStatus(SponsorNonceSlotStatus.CONSUMED)
                .activeTxId(transactionId)
                .consumedTxId(transactionId)
                .stateChangedAt(command.stateChangedAt())
                .consumedReason(command.consumedReason())
                .hasTxHash(true)
                .hasSigningEvidence(true)
                .hasBroadcastEvidence(true)
                .hasReceiptEvidence(true)
                .build());
        return;
      } catch (Web3TransactionStateInvalidException e) {
        if (isSlotNotFound(e)) {
          log.warn(
              "Skipping nonce slot consumed transition for txId={}: {}",
              transactionId,
              e.getMessage());
          return;
        }
        if (isStaleActual(e, SponsorNonceSlotStatus.CONSUMED)) {
          if (isSlotConsumedByTransaction(command, transactionId)) {
            log.warn(
                "Skipping already-consumed nonce slot transition for txId={}: {}",
                transactionId,
                e.getMessage());
            return;
          }
          throw e;
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

  private static void validate(
      Long transactionId,
      String idempotencyKey,
      Web3ReferenceType referenceType,
      String referenceId) {
    if (transactionId == null || transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new Web3InvalidInputException("idempotencyKey is required");
    }
    if (referenceType == null) {
      throw new Web3InvalidInputException("referenceType is required");
    }
    if (referenceId == null || referenceId.isBlank()) {
      throw new Web3InvalidInputException("referenceId is required");
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

  private boolean isSlotConsumedByTransaction(
      SponsorNonceReceiptCommand command, Long transactionId) {
    if (transactionId == null || command.fromAddress() == null || command.fromAddress().isBlank()) {
      return false;
    }
    return nonceSlotLifecycleUseCase
        .loadSlotsForReview(command.chainId(), command.fromAddress())
        .stream()
        .filter(slot -> slot.nonce() == command.nonce())
        .anyMatch(slot -> isConsumedSlotForTransaction(slot, transactionId));
  }

  private boolean isConsumedSlotForTransaction(SponsorNonceSlotView slot, Long transactionId) {
    return slot.status() == SponsorNonceSlotStatus.CONSUMED
        && transactionId.equals(slot.consumedTxId());
  }

  public record SponsorNonceReceiptCommand(
      long chainId,
      String fromAddress,
      Long nonce,
      String consumedReason,
      LocalDateTime stateChangedAt) {}
}
