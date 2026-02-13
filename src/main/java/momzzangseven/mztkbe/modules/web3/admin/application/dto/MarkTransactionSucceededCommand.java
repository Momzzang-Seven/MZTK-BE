package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3ValidationMessage;

public record MarkTransactionSucceededCommand(
    Long operatorId,
    Long transactionId,
    String txHash,
    String explorerUrl,
    String reason,
    String evidence) {

  public void validate() {
    validatePositive(operatorId, "operatorId");
    validatePositive(transactionId, "transactionId");
    validateTxHash(txHash);
    validateRequired(explorerUrl, "explorerUrl");
    validateRequired(reason, "reason");
    validateRequired(evidence, "evidence");
  }

  private static void validatePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new Web3InvalidInputException(fieldName + " must be positive");
    }
  }

  private static void validateRequired(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new Web3InvalidInputException(fieldName + " is required");
    }
  }

  private static void validateTxHash(String txHash) {
    if (txHash == null || txHash.isBlank()) {
      throw new Web3InvalidInputException(Web3ValidationMessage.TX_HASH_REQUIRED);
    }
    if (!txHash.matches("^0x[0-9a-fA-F]{64}$")) {
      throw new Web3InvalidInputException("txHash must be 0x-prefixed 32-byte hex");
    }
  }
}
