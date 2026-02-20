package momzzangseven.mztkbe.modules.web3.transaction.domain.event;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;

/** Domain event published when a web3 transaction is confirmed as SUCCEEDED. */
public record Web3TransactionSucceededEvent(
    Long transactionId,
    String idempotencyKey,
    Web3ReferenceType referenceType,
    String referenceId,
    Long fromUserId,
    Long toUserId,
    String txHash) {

  public Web3TransactionSucceededEvent {
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
    if (txHash != null && !txHash.isBlank() && !txHash.matches("^0x[0-9a-fA-F]{64}$")) {
      throw new Web3InvalidInputException("txHash must be 0x-prefixed 32-byte hex");
    }
  }
}
