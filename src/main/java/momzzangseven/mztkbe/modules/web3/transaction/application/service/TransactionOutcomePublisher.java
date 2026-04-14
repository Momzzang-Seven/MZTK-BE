package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionFailedOnchainEvent;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionSucceededEvent;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
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
public class TransactionOutcomePublisher {

  private final UpdateTransactionPort updateTransactionPort;
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
}
