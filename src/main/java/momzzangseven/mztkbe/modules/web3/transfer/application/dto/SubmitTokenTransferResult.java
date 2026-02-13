package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import lombok.Builder;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

@Builder
public record SubmitTokenTransferResult(Long transactionId, String status, String txHash) {

  public SubmitTokenTransferResult {
    validate(transactionId, status, txHash);
  }

  private static void validate(Long transactionId, String status, String txHash) {
    if (transactionId == null || transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }
    if (status == null || status.isBlank()) {
      throw new Web3InvalidInputException("status is required");
    }
    if (txHash == null || txHash.isBlank()) {
      throw new Web3InvalidInputException("txHash is required");
    }
  }
}
