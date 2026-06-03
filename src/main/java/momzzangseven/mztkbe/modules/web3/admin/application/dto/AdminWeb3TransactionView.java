package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record AdminWeb3TransactionView(
    Long transactionId,
    String idempotencyKey,
    String referenceType,
    String referenceId,
    String txType,
    Long fromUserId,
    Long toUserId,
    String fromAddress,
    String toAddress,
    String status,
    String txHash,
    String failureReason,
    String processingBy,
    LocalDateTime processingUntil,
    LocalDateTime signedAt,
    LocalDateTime broadcastedAt,
    LocalDateTime confirmedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public AdminWeb3TransactionView {
    if (transactionId == null || transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }
    if (status == null || status.isBlank()) {
      throw new Web3InvalidInputException("status is required");
    }
  }
}
