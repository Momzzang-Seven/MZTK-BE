package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import lombok.Builder;
import momzzangseven.mztkbe.global.error.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

@Builder
public record MarkTransactionSucceededResult(
    Long transactionId,
    Web3TxStatus status,
    Web3TxStatus previousStatus,
    String txHash,
    String explorerUrl) {

  public MarkTransactionSucceededResult {
    validate(transactionId, status, previousStatus, txHash, explorerUrl);
  }

  private static void validate(
      Long transactionId,
      Web3TxStatus status,
      Web3TxStatus previousStatus,
      String txHash,
      String explorerUrl) {
    if (transactionId == null || transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }
    if (status == null) {
      throw new Web3InvalidInputException("status is required");
    }
    if (previousStatus == null) {
      throw new Web3InvalidInputException("previousStatus is required");
    }
    if (txHash == null || txHash.isBlank()) {
      throw new Web3InvalidInputException("txHash is required");
    }
    if (explorerUrl == null || explorerUrl.isBlank()) {
      throw new Web3InvalidInputException("explorerUrl is required");
    }
  }
}
