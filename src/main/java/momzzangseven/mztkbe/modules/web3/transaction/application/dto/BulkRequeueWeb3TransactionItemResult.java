package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

public record BulkRequeueWeb3TransactionItemResult(
    Long transactionId,
    TransactionRequeueItemResultType result,
    Web3TxStatus status,
    Web3TxStatus previousStatus,
    String originalFailureReason,
    String reason) {

  public BulkRequeueWeb3TransactionItemResult {
    if (transactionId == null || transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }
    if (result == null) {
      throw new Web3InvalidInputException("result is required");
    }
    if ((result == TransactionRequeueItemResultType.REQUEUED
            || result == TransactionRequeueItemResultType.REJECTED)
        && status == null) {
      throw new Web3InvalidInputException("status is required for evaluated transaction");
    }
  }
}
