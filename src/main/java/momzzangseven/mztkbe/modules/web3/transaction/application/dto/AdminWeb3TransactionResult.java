package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;

public record AdminWeb3TransactionResult(
    Long transactionId,
    String idempotencyKey,
    Web3ReferenceType referenceType,
    String referenceId,
    Web3TxType txType,
    Long fromUserId,
    Long toUserId,
    String fromAddress,
    String toAddress,
    Web3TxStatus status,
    String txHash,
    String failureReason,
    String processingBy,
    LocalDateTime processingUntil,
    LocalDateTime signedAt,
    LocalDateTime broadcastedAt,
    LocalDateTime confirmedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public AdminWeb3TransactionResult {
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
    if (txType == null) {
      throw new Web3InvalidInputException("txType is required");
    }
    if (status == null) {
      throw new Web3InvalidInputException("status is required");
    }
  }
}
