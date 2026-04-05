package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

public record ExecutionTransactionSummary(Long transactionId, Web3TxStatus status, String txHash) {

  public ExecutionTransactionSummary {
    if (transactionId == null || transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }
    if (status == null) {
      throw new Web3InvalidInputException("status is required");
    }
  }
}
