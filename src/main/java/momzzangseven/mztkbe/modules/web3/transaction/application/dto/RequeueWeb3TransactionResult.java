package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

public record RequeueWeb3TransactionResult(
    Long transactionId,
    Web3TxStatus status,
    Web3TxStatus previousStatus,
    String originalFailureReason,
    boolean requeued) {

  public RequeueWeb3TransactionResult {
    if (transactionId == null || transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }
    if (status == null) {
      throw new Web3InvalidInputException("status is required");
    }
    if (previousStatus == null) {
      throw new Web3InvalidInputException("previousStatus is required");
    }
    if (originalFailureReason == null || originalFailureReason.isBlank()) {
      throw new Web3InvalidInputException("originalFailureReason is required");
    }
  }
}
