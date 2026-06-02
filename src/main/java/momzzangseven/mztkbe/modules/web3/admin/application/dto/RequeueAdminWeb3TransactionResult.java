package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record RequeueAdminWeb3TransactionResult(
    Long transactionId,
    String status,
    String previousStatus,
    String originalFailureReason,
    boolean requeued) {

  public RequeueAdminWeb3TransactionResult {
    if (transactionId == null || transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }
    if (status == null || status.isBlank()) {
      throw new Web3InvalidInputException("status is required");
    }
    if (previousStatus == null || previousStatus.isBlank()) {
      throw new Web3InvalidInputException("previousStatus is required");
    }
  }
}
