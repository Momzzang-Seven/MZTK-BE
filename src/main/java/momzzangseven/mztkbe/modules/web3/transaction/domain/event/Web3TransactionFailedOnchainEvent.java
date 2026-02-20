package momzzangseven.mztkbe.modules.web3.transaction.domain.event;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;

/** Domain event published when a web3 transaction is finalized as FAILED_ONCHAIN. */
public record Web3TransactionFailedOnchainEvent(
    Long transactionId,
    String idempotencyKey,
    Web3ReferenceType referenceType,
    String referenceId,
    Long fromUserId,
    Long toUserId,
    String txHash,
    String failureReason) {

  public Web3TransactionFailedOnchainEvent {
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
    if (failureReason == null || failureReason.isBlank()) {
      throw new Web3InvalidInputException("failureReason is required");
    }
  }
}
