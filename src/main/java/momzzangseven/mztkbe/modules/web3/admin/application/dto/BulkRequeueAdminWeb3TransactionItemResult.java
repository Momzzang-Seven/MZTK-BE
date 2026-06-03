package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record BulkRequeueAdminWeb3TransactionItemResult(
    Long transactionId,
    String result,
    String status,
    String previousStatus,
    String originalFailureReason,
    String reason) {

  public BulkRequeueAdminWeb3TransactionItemResult {
    if (transactionId == null || transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }
    if (result == null || result.isBlank()) {
      throw new Web3InvalidInputException("result is required");
    }
  }
}
